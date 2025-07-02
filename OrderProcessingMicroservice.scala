import zio.json._
import java.time.Instant
import java.util.UUID

// Represents a single item within an order
case class OrderItem(
    productId: String,
    quantity: Int,
    price: Double
) derives JsonCodec

// Represents an incoming order
case class Order(
    orderId: UUID,
    customerId: String,
    items: List[OrderItem],
    totalAmount: Double,
    orderDate: Instant
) derives JsonCodec

// Represents a processed order confirmation
case class OrderProcessed(
    orderId: UUID,
    status: String,
    errorMessage: Option[String],
    processedDate: Instant
) derives JsonCodec

// Domain Errors
sealed trait OrderError extends Product with Serializable

case class InvalidOrderData(message: String) extends OrderError
case class PaymentGatewayError(message: String, isTransient: Boolean) extends OrderError
case class DatabaseError(message: String) extends OrderError
case class KafkaPublishError(cause: Throwable) extends OrderError
case class UnknownOrderError(message: String) extends OrderError

// Service Interfaces and Implementations

import zio._
import zio.kafka.consumer._
import zio.kafka.producer._
import zio.kafka.serde._
import org.apache.kafka.clients.producer.ProducerRecord
import zio.json._
import java.io.IOException
import scala.collection.concurrent.TrieMap

// 1. Database Service (Simulated In-Memory)
trait DatabaseService {
  def saveOrder(order: Order): ZIO[Any, DatabaseError, Unit]
  def getOrder(orderId: UUID): ZIO[Any, DatabaseError, Option[Order]]
}

object DatabaseService {
  val live: ZLayer[Any, Nothing, DatabaseService] = ZLayer.fromZIO(
    Ref.make(TrieMap.empty[UUID, Order]).map {
      dbRef =>
        new DatabaseService {
          override def saveOrder(order: Order): ZIO[Any, DatabaseError, Unit] = {
            dbRef.update(db => db + (order.orderId -> order)) *> 
            ZIO.console.printLine(s"[DB] Saved order ${order.orderId}").mapError(e => DatabaseError(e.getMessage))
          }

          override def getOrder(orderId: UUID): ZIO[Any, DatabaseError, Option[Order]] = {
            dbRef.get.map(_.get(orderId)) <* 
            ZIO.console.printLine(s"[DB] Retrieved order ${orderId}").mapError(e => DatabaseError(e.getMessage))
          }
        }
    }
  )
}

// 2. Payment Gateway (Simulated External Service)
trait PaymentGateway {
  def authorizePayment(order: Order): ZIO[Any, PaymentGatewayError, Unit]
}

object PaymentGateway {
  val live: ZLayer[Any, Nothing, PaymentGateway] = ZLayer.succeed(
    new PaymentGateway {
      private val random = new scala.util.Random

      override def authorizePayment(order: Order): ZIO[Any, PaymentGatewayError, Unit] = {
        for {
          _ <- ZIO.console.printLine(s"[PaymentGateway] Attempting to authorize payment for order ${order.orderId}...")
          result <- ZIO.attempt {
            val r = random.nextDouble()
            if (r < 0.1) { // 10% chance of transient failure
              throw new RuntimeException("Payment gateway transient error: Connection timed out.")
            } else if (r < 0.02) { // 2% chance of permanent failure (e.g., invalid card)
              throw new IllegalArgumentException("Payment gateway permanent error: Invalid card details.")
            } else {
              // Success
            }
          }.mapError {
            case e: RuntimeException => PaymentGatewayError(e.getMessage, isTransient = true)
            case e: IllegalArgumentException => PaymentGatewayError(e.getMessage, isTransient = false)
            case e => PaymentGatewayError(s"Unknown payment error: ${e.getMessage}", isTransient = false)
          }
          _ <- ZIO.console.printLine(s"[PaymentGateway] Payment authorized for order ${order.orderId}")
        } yield result
      }
    }
  )
}

// 3. Order Validator
trait OrderValidator {
  def validate(order: Order): ZIO[Any, InvalidOrderData, Unit]
}

object OrderValidator {
  val live: ZLayer[Any, Nothing, OrderValidator] = ZLayer.succeed(
    new OrderValidator {
      override def validate(order: Order): ZIO[Any, InvalidOrderData, Unit] = {
        val errors = List.newBuilder[String]
        if (order.items.isEmpty) {
          errors += "Order must contain at least one item."
        }
        if (order.totalAmount <= 0) {
          errors += "Order total amount must be positive."
        }
        order.items.foreach {
          item =>
            if (item.quantity <= 0) {
              errors += s"Product ${item.productId} quantity must be positive."
            }
            if (item.price <= 0) {
              errors += s"Product ${item.productId} price must be positive."
            }
        }

        val collectedErrors = errors.result()
        if (collectedErrors.nonEmpty) {
          ZIO.fail(InvalidOrderData(collectedErrors.mkString("; ")))
        } else {
          ZIO.unit
        }
      }
    }
  )
}

// 4. Kafka Service
trait KafkaService {
  def consumeOrders: ZStream[Any, Throwable, CommittableRecord[String, String]]
  def publishOrderProcessed(orderProcessed: OrderProcessed): ZIO[Any, KafkaPublishError, Unit]
  def sendToDeadLetterQueue(originalMessage: String, errorDetails: String): ZIO[Any, KafkaPublishError, Unit]
}

object KafkaService {
  val live: ZLayer[Consumer, KafkaProducer, KafkaService] = ZLayer.fromZIO(
    for {
      consumer <- ZIO.service[Consumer]
      producer <- ZIO.service[KafkaProducer]
    } yield new KafkaService {
      private val orderTopic = "orders"
      private val processedOrderTopic = "order-processed"
      private val deadLetterTopic = "orders-dlq"

      private val keySerde = Serde.string
      private val valueSerde = Serde.string

      override def consumeOrders: ZStream[Any, Throwable, CommittableRecord[String, String]] = {
        Consumer.plainStream(Subscription.topics(orderTopic), keySerde, valueSerde)
      }

      override def publishOrderProcessed(orderProcessed: OrderProcessed): ZIO[Any, KafkaPublishError, Unit] = {
        val jsonPayload = orderProcessed.toJson
        val record = new ProducerRecord(processedOrderTopic, orderProcessed.orderId.toString, jsonPayload)
        producer.produce(record)
          .tap(_ => ZIO.console.printLine(s"[Kafka] Published processed order ${orderProcessed.orderId} to $processedOrderTopic"))
          .mapError(t => KafkaPublishError(t))
          .unit
      }

      override def sendToDeadLetterQueue(originalMessage: String, errorDetails: String): ZIO[Any, KafkaPublishError, Unit] = {
        val record = new ProducerRecord(deadLetterTopic, "dlq-key", s"Error: $errorDetails | Original: $originalMessage")
        producer.produce(record)
          .tap(_ => ZIO.console.printLine(s"[Kafka] Sent message to DLQ: $errorDetails"))
          .mapError(t => KafkaPublishError(t))
          .unit
      }
    }
  )

  val consumerLayer: ZLayer[Any, Throwable, Consumer] = 
    ZLayer.make[Consumer](
      Consumer.group("order-processing-group"),
      Consumer.autoOffsetReset(OffsetReset.Earliest),
      Consumer.consumer(),
      Consumer.bootstrapServers(List("localhost:9092"))
    )

  val producerLayer: ZLayer[Any, Throwable, KafkaProducer] = 
    ZLayer.make[KafkaProducer](
      KafkaProducer.live, 
      Producer.bootstrapServers(List("localhost:9092"))
    )

  val kafkaLive: ZLayer[Any, Throwable, KafkaService] = 
    consumerLayer ++ producerLayer >>> KafkaService.live
}

// 5. Order Processor
trait OrderProcessor {
  def processOrders: ZIO[Any, Throwable, Unit]
}

object OrderProcessor {
  val retrySchedule = Schedule.exponential(100.millis) || Schedule.recurs(5)

  val live: ZLayer[
    DatabaseService with PaymentGateway with OrderValidator with KafkaService,
    Nothing,
    OrderProcessor
  ] = ZLayer.fromFunction(
    (dbService: DatabaseService, paymentGateway: PaymentGateway, orderValidator: OrderValidator, kafkaService: KafkaService) =>
      new OrderProcessor {
        private def processRecord(recordValue: String): ZIO[Any, OrderError, Unit] = {
          for {
            _ <- ZIO.console.printLine(s"[Processor] Attempting to process record: $recordValue")
            order <- ZIO.fromEither(recordValue.fromJson[Order])
                         .mapError(err => InvalidOrderData(s"Failed to parse order JSON: $err. Raw: $recordValue"))
            _ <- orderValidator.validate(order)
            _ <- paymentGateway.authorizePayment(order).retry(Schedule.recurWhile[PaymentGatewayError](_.isTransient) && retrySchedule)
                   .tapError {
                     case e: PaymentGatewayError => ZIO.console.printLine(s"[Processor] Payment authorization failed for order ${order.orderId}: ${e.message}. Transient: ${e.isTransient}")
                     case e => ZIO.console.printLine(s"[Processor] Unexpected error during payment authorization for order ${order.orderId}: ${e.getMessage}")
                   }
            _ <- dbService.saveOrder(order)
            processedOrder = OrderProcessed(order.orderId, "SUCCESS", None, Instant.now())
            _ <- kafkaService.publishOrderProcessed(processedOrder)
            _ <- ZIO.console.printLine(s"[Processor] Successfully processed order ${order.orderId}")
          } yield ()
        }

        override def processOrders: ZIO[Any, Throwable, Unit] = {
          kafkaService.consumeOrders
            .mapZIOPar(10) { record => // Process 10 messages concurrently
              processRecord(record.value)
                .tapError(e => ZIO.console.printLine(s"[Processor] Error processing record ${record.value}: $e"))
                .catchAll { error =>
                  kafkaService.sendToDeadLetterQueue(record.value, error.toString)
                }
                .unit
                .as(record.offset)
            }
            .aggregate(Consumer.offsetBatches)
            .mapZIO(_.commit)
            .runDrain
            .tapError(e => ZIO.console.printLine(s"[Processor] Kafka stream failed: $e"))
        }
      }
  )
}

// Main Application
object OrderProcessingApp extends ZIOAppDefault {
  override def run: ZIO[Any, Throwable, Unit] = {
    val appLayers = 
      DatabaseService.live ++
      PaymentGateway.live ++
      OrderValidator.live ++
      KafkaService.kafkaLive

    ZIO.scoped {
      OrderProcessor.live.build.map(_.get[OrderProcessor]).flatMap(_.processOrders)
    }.provide(
      appLayers,
      // Kafka Consumer and Producer layers are provided by KafkaService.kafkaLive
      Consumer.group("order-processing-group"),
      Consumer.autoOffsetReset(OffsetReset.Earliest),
      Consumer.consumer(),
      Consumer.bootstrapServers(List("localhost:9092")),
      Producer.producer(),
      Producer.bootstrapServers(List("localhost:9092")),
      Scope.default
    )
  }
}
