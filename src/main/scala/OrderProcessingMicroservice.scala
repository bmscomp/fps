import zio._
import zio.json._
import zio.kafka.consumer._
import zio.kafka.producer._
import zio.kafka.serde._
import zio.stream._
import zio.stm._
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Instant
import java.util.UUID
import scala.collection.immutable.TrieMap
import scala.util.Random

// --- 1. Data Models ---

case class OrderItem(
    productId: String,
    quantity: Int,
    price: Double
) derives JsonCodec

case class Order(
    orderId: UUID,
    customerId: String,
    items: List[OrderItem],
    totalAmount: Double,
    orderDate: Instant
) derives JsonCodec

case class OrderProcessed(
    orderId: UUID,
    status: String,
    errorMessage: Option[String],
    processedDate: Instant
) derives JsonCodec

// --- 2. Domain Errors ---

sealed trait OrderError extends Product with Serializable
case class InvalidOrderData(message: String) extends OrderError
case class PaymentGatewayError(message: String, isTransient: Boolean) extends OrderError
case class DatabaseError(message: String) extends OrderError
case class KafkaPublishError(cause: Throwable) extends OrderError
case class UnknownOrderError(message: String) extends OrderError

// --- 3. Services ---

// Database Service
trait DatabaseService {
  def saveOrder(order: Order): ZIO[Any, DatabaseError, Unit]
  def getOrder(orderId: UUID): ZIO[Any, DatabaseError, Option[Order]]
}

object DatabaseService {
  // Live implementation using in-memory TrieMap for simulation
  val live: ZLayer[Any, Nothing, DatabaseService] = ZLayer.fromZIO(
    Ref.make(TrieMap.empty[UUID, Order]).map {
      db => new DatabaseService {
        override def saveOrder(order: Order): ZIO[Any, DatabaseError, Unit] = 
          ZIO.succeed(db.update(_ + (order.orderId -> order))).unit
            .tap(_ => ZIO.console.printLine(s"[DB] Saved order ${order.orderId}"))
            .mapError(e => DatabaseError(s"Failed to save order: ${e.getMessage}"))

        override def getOrder(orderId: UUID): ZIO[Any, DatabaseError, Option[Order]] = 
          ZIO.succeed(db.get.apply().get(orderId))
            .mapError(e => DatabaseError(s"Failed to get order: ${e.getMessage}"))
      }
    }
  )
}

// Payment Gateway Service
trait PaymentGateway {
  def authorizePayment(order: Order): ZIO[Any, PaymentGatewayError, Unit]
}

object PaymentGateway {
  val live: ZLayer[Any, Nothing, PaymentGateway] = ZLayer.succeed(
    new PaymentGateway {
      override def authorizePayment(order: Order): ZIO[Any, PaymentGatewayError, Unit] = 
        ZIO.random.nextIntBetween(1, 100).flatMap {
          case n if n <= 5 => // Simulate transient failure 5% of the time
            ZIO.fail(PaymentGatewayError(s"Transient payment error for ${order.orderId}", isTransient = true))
          case n if n <= 7 => // Simulate permanent failure 2% of the time
            ZIO.fail(PaymentGatewayError(s"Permanent payment error for ${order.orderId}", isTransient = false))
          case _ => 
            ZIO.succeed(())
              .tap(_ => ZIO.console.printLine(s"[Payment] Authorized payment for ${order.orderId}"))
        }
    }
  )
}

// Order Validator Service
trait OrderValidator {
  def validate(order: Order): ZIO[Any, InvalidOrderData, Unit]
}

object OrderValidator {
  val live: ZLayer[Any, Nothing, OrderValidator] = ZLayer.succeed(
    new OrderValidator {
      override def validate(order: Order): ZIO[Any, InvalidOrderData, Unit] = {
        if (order.items.isEmpty) {
          ZIO.fail(InvalidOrderData(s"Order ${order.orderId} has no items"))
        } else if (order.totalAmount <= 0) {
          ZIO.fail(InvalidOrderData(s"Order ${order.orderId} has invalid total amount ${order.totalAmount}"))
        } else {
          ZIO.succeed(()).tap(_ => ZIO.console.printLine(s"[Validator] Validated order ${order.orderId}"))
        }
      }
    }
  )
}

// Kafka Service
trait KafkaService {
  def consumeOrders: ZStream[Any, Throwable, CommittableRecord[String, String]]
  def publishOrderProcessed(orderProcessed: OrderProcessed): ZIO[Any, KafkaPublishError, Unit]
  def sendToDeadLetterQueue(originalRecord: String, error: OrderError): ZIO[Any, KafkaPublishError, Unit]
}

object KafkaService {
  val orderTopic = "orders"
  val processedOrderTopic = "order-processed"
  val dlqTopic = "orders-dlq"
  val consumerGroup = "order-processing-group"
  val bootstrapServers = List("localhost:9092")

  val consumerLayer: ZLayer[Any, Throwable, Consumer] = 
    ZLayer.make[Consumer](
      Consumer.group(consumerGroup),
      Consumer.autoOffsetReset(AutoOffsetReset.Earliest),
      Consumer.enableAutoCommit,
      Consumer.bootstrapServers(bootstrapServers),
      Consumer.with  ClientId("order-consumer")
    )

  val producerLayer: ZLayer[Any, Throwable, Producer] = 
    ZLayer.make[Producer](
      Producer.bootstrapServers(bootstrapServers),
      Producer.withClientId("order-producer")
    )

  val live: ZLayer[Consumer with Producer, Throwable, KafkaService] = ZLayer.fromFunction(
    (consumer: Consumer, producer: Producer) => new KafkaService {
      private val keySerde = Serde.string
      private val valueSerde = Serde.string

      override def consumeOrders: ZStream[Any, Throwable, CommittableRecord[String, String]] = 
        consumer.plainStream(Subscription.topics(orderTopic), keySerde, valueSerde)

      override def publishOrderProcessed(orderProcessed: OrderProcessed): ZIO[Any, KafkaPublishError, Unit] = {
        val jsonPayload = orderProcessed.toJson
        val record = new ProducerRecord(processedOrderTopic, orderProcessed.orderId.toString, jsonPayload)
        producer.produce(record)
          .tap(_ => ZIO.console.printLine(s"[Kafka] Published processed order ${orderProcessed.orderId} to $processedOrderTopic"))
          .mapError(t => KafkaPublishError(t))
          .unit
      }

      override def sendToDeadLetterQueue(originalRecord: String, error: OrderError): ZIO[Any, KafkaPublishError, Unit] = {
        val dlqPayload = Json.obj(
          "originalRecord" -> originalRecord.toJson,
          "errorType" -> error.productPrefix.toJson,
          "errorMessage" -> error.toString.toJson,
          "timestamp" -> Instant.now().toString.toJson
        ).toJson

        val record = new ProducerRecord(dlqTopic, "dlq-key", dlqPayload)
        producer.produce(record)
          .tap(_ => ZIO.console.printLine(s"[Kafka] Sent message to DLQ for error: $error"))
          .mapError(t => KafkaPublishError(t))
          .unit
      }
    }
  )
}

// --- 4. Order Processor ---

trait OrderProcessor {
  def processOrders: ZIO[Any, Throwable, Unit]
}

object OrderProcessor {
  val live: ZLayer[
    DatabaseService 
    with PaymentGateway 
    with OrderValidator 
    with KafkaService,
    Nothing,
    OrderProcessor
  ] = ZLayer.fromFunction(
    (dbService: DatabaseService,
     paymentGateway: PaymentGateway,
     orderValidator: OrderValidator,
     kafkaService: KafkaService) => new OrderProcessor {

      private val retrySchedule = Schedule.exponential(100.millis) && Schedule.recurs(3)
      private val parallelism = 10

      private def processRecord(recordValue: String): ZIO[Any, OrderError, Unit] = 
        for {
          _ <- ZIO.console.printLine(s"[Processor] Attempting to process record: $recordValue")
          order <- ZIO.fromEither(recordValue.fromJson[Order])
                     .mapError(err => InvalidOrderData(s"Failed to parse order JSON: $err. Raw: $recordValue"))
          _ <- orderValidator.validate(order)
          _ <- paymentGateway.authorizePayment(order).retry(
            Schedule.recurWhile[PaymentGatewayError](_.isTransient) && retrySchedule
          ).tapError {
            case pge: PaymentGatewayError if pge.isTransient => 
              ZIO.console.printLine(s"[Processor] Transient payment error for ${order.orderId}: ${pge.message}. Retrying...")
            case pge: PaymentGatewayError => 
              ZIO.console.printLine(s"[Processor] Permanent payment error for ${order.orderId}: ${pge.message}. Not retrying.")
            case other => ZIO.console.printLine(s"[Processor] Unexpected error during payment for ${order.orderId}: $other")
          }
          _ <- dbService.saveOrder(order)
          processedOrder = OrderProcessed(order.orderId, "SUCCESS", None, Instant.now())
          _ <- kafkaService.publishOrderProcessed(processedOrder)
          _ <- ZIO.console.printLine(s"[Processor] Successfully processed order ${order.orderId}")
        } yield ()

      override def processOrders: ZIO[Any, Throwable, Unit] = 
        kafkaService.consumeOrders
          .mapZIOPar(parallelism) {
            record => 
              processRecord(record.value).catchAll {
                case e: OrderError => 
                  ZIO.console.printLine(s"[Processor] Failed to process record ${record.value} due to $e. Sending to DLQ.") *> 
                  kafkaService.sendToDeadLetterQueue(record.value, e)
                case t: Throwable => 
                  ZIO.console.printLine(s"[Processor] Unexpected critical error processing record ${record.value}: $t. Sending to DLQ.") *> 
                  kafkaService.sendToDeadLetterQueue(record.value, UnknownOrderError(t.getMessage))
              } ensuring (record.offset.commit)
          }
          .runDrain // Run the stream indefinitely
    }
  )
}

// --- 5. Main Application ---

object OrderProcessingApp extends ZIOAppDefault {

  override def run: ZIO[Any, Throwable, Unit] = {
    val appLayers = 
      DatabaseService.live ++
      PaymentGateway.live ++
      OrderValidator.live ++
      (KafkaService.consumerLayer ++ KafkaService.producerLayer) >>> KafkaService.live

    OrderProcessor.live.provide(appLayers).flatMap(_.processOrders)
  }
}
