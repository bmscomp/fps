# 5b. Robust Order Processing Microservice

This section presents a more complex and realistic ZIO application: a robust order processing microservice. This project demonstrates advanced ZIO features such as Kafka integration, sophisticated error handling, concurrency, resource management, and dependency injection using `ZLayer`.

### Project Overview

Our microservice will perform the following tasks:

1.  **Consume Orders:** Read order messages from an input Kafka topic (`orders`).
2.  **Validate Orders:** Apply business rules to validate incoming orders.
3.  **Process Payments:** Simulate interaction with an external payment gateway, handling transient and permanent failures with retries.
4.  **Store Orders:** Save validated and authorized orders to a persistent store (simulated in-memory for simplicity).
5.  **Publish Confirmations:** Send processing confirmations to an output Kafka topic (`order-processed`).
6.  **Handle Failures:** Implement a Dead Letter Queue (DLQ) for messages that cannot be processed successfully.

### Code: `OrderProcessingMicroservice.scala`

Due to the size and complexity of this project, the full Scala code is provided in a separate file named `OrderProcessingMicroservice.scala`. You should create this file in your project's `src/main/scala` directory (or similar, depending on your project structure) and copy the provided code into it.

```scala
// The full code for the Order Processing Microservice is in:
// src/main/scala/OrderProcessingMicroservice.scala

// Key components include:
// - Data Models (OrderItem, Order, OrderProcessed)
// - Domain Errors (OrderError ADT)
// - Service Interfaces (DatabaseService, PaymentGateway, OrderValidator, KafkaService)
// - Live Implementations for each service
// - OrderProcessor: Orchestrates the processing flow, handles retries, DLQ
// - OrderProcessingApp: Main application entry point, composes ZLayers
```

### Explanation of Key Concepts Demonstrated:

*   **Data Models & `zio-json`**: Defines `OrderItem`, `Order`, `OrderProcessed` with automatic JSON codecs using `derives JsonCodec`.
*   **Domain-Specific Error Handling**: Uses a sealed trait `OrderError` with specific error cases (`InvalidOrderData`, `PaymentGatewayError`, `DatabaseError`, `KafkaPublishError`, `UnknownOrderError`) for type-safe and exhaustive error management.
*   **Modular Service Design with `ZLayer`**: Each major component (Database, Payment Gateway, Kafka, Order Validation) is defined as a service trait with a `ZLayer` providing its live implementation. This enables clear separation of concerns, testability, and flexible dependency injection.
*   **Kafka Integration (`zio-kafka`)**: Demonstrates consuming messages from an input topic, producing messages to an output topic, and sending failed messages to a Dead Letter Queue (DLQ).
    *   Uses `Consumer.plainStream` for consuming and `Producer.produce` for publishing.
    *   Handles Kafka offset committing.
*   **Concurrency (`ZStream.mapZIOPar`)**: The `OrderProcessor` uses `ZStream.mapZIOPar` to process Kafka messages concurrently, allowing for high throughput by processing multiple orders in parallel while maintaining backpressure.
*   **Retries (`Schedule`)**: The `PaymentGateway` simulation includes transient errors, which are handled using ZIO's `Schedule` for retries with exponential backoff and a maximum number of attempts. This ensures resilience against temporary external service outages.
*   **Resource Management**: Implicitly handled by `ZLayer` for services like Kafka consumer/producer, ensuring resources are acquired and released correctly.
*   **Application Composition**: The `OrderProcessingApp` uses `ZLayer`'s composition operators (`>>>`, `++`) to build the entire dependency graph, wiring all services together to form the complete application.
*   **Logging**: Basic logging is integrated throughout the services to provide visibility into the processing flow and error conditions.

### Running the Microservice

To run this microservice, you will need a running Kafka instance. The easiest way to set this up is using Docker Compose. Make sure you have Docker installed.

1.  **Save the Scala Code**: Save the entire Scala code for the microservice (from the previous session or the provided `OrderProcessingMicroservice.scala` file) into a file named `OrderProcessingMicroservice.scala` in your project's `src/main/scala` directory (or similar, depending on your project structure).

2.  **Update `build.sbt`**: Ensure your `build.sbt` includes the necessary dependencies:

    ```scala
    // build.sbt
    val zioVersion = "2.0.21"
    val zioJsonVersion = "0.6.0"
    val zioKafkaVersion = "2.0.7"

    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-json" % zioJsonVersion,
      "dev.zio" %% "zio-kafka" % zioKafkaVersion,
      // Add other dependencies as needed
    )
    ```

3.  **Kafka Setup (Docker Compose)**:

    Create a `docker-compose.yml` file in your project root:

    ```yaml
    version: '3.8'
    services:
      zookeeper:
        image: confluentinc/cp-zookeeper:7.0.1
        hostname: zookeeper
        container_name: zookeeper
        ports:
          - "2181:2181"
        environment:
          ZOOKEEPER_CLIENT_PORT: 2181
          ZOOKEEPER_TICK_TIME: 2000

      kafka:
        image: confluentinc/cp-kafka:7.0.1
        hostname: kafka
        container_name: kafka
        ports:
          - "9092:9092"
          - "9094:9094"
        environment:
          KAFKA_BROKER_ID: 1
          KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
          KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
          KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
          KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
          KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
          KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
        depends_on:
          - zookeeper
    ```

    Start Kafka and Zookeeper:

    ```bash
    docker-compose up -d
    ```

4.  **Create Kafka Topics**: Once Kafka is running, create the necessary topics. You can do this by executing commands inside the Kafka container:

    ```bash
    # Access the Kafka container shell
    docker exec -it kafka bash

    # Create 'orders' topic
    kafka-topics --bootstrap-server localhost:29092 --create --topic orders --partitions 1 --replication-factor 1

    # Create 'order-processed' topic
    kafka-topics --bootstrap-server localhost:29092 --create --topic order-processed --partitions 1 --replication-factor 1

    # Create 'orders-dlq' topic
    kafka-topics --bootstrap-server localhost:29092 --create --topic orders-dlq --partitions 1 --replication-factor 1

    # Exit the container shell
    exit
    ```

5.  **Run the Application**: From your sbt project directory, run the application:

    ```bash
    sbt "runMain OrderProcessingApp"
    ```

6.  **Produce Test Messages**: While the application is running, you can produce test messages to the `orders` topic using another Kafka console producer:

    ```bash
    # Access the Kafka container shell
    docker exec -it kafka bash

    # Start console producer
    kafka-console-producer --broker-list localhost:29092 --topic orders
    ```

    Then, in the producer's console, type JSON messages (one per line) and press Enter:

    ```json
    {"orderId":"a1b2c3d4-e5f6-7890-1234-567890abcdef","customerId":"customer-123","items":[{"productId":"prod-001","quantity":2,"price":10.0}],"totalAmount":20.0,"orderDate":"2023-01-01T10:00:00Z"}
    {"orderId":"b2c3d4e5-f6a7-8901-2345-67890abcdef0","customerId":"customer-124","items":[{"productId":"prod-002","quantity":1,"price":50.0}],"totalAmount":50.0,"orderDate":"2023-01-01T10:05:00Z"}
    {"orderId":"c3d4e5f6-a7b8-9012-3456-7890abcdef01","customerId":"customer-125","items":[],"totalAmount":10.0,"orderDate":"2023-01-01T10:10:00Z"} // Invalid: empty items
    ```

    Observe the output in your running `OrderProcessingApp` console. You should see messages indicating processing, saving, publishing, and potentially errors or DLQ messages for invalid orders.

### Further Enhancements (Optional Exercises)

*   **Configuration Management**: Use `ZIO Config` to externalize Kafka broker addresses, topic names, and retry policies.
*   **Real Database Integration**: Replace the in-memory `DatabaseService` with a real database (e.g., PostgreSQL with `zio-sql` or `Doobie`).
*   **Monitoring and Logging**: Integrate with a logging framework (e.g., `zio-logging`) and metrics (e.g., Prometheus).
*   **Authentication/Authorization**: Add security layers for real-world microservice scenarios.
*   **Testing**: Write comprehensive unit and integration tests for each service using `zio-test`.

This project provides a solid foundation for building complex, resilient, and scalable microservices with Scala 3 and ZIO. It showcases many advanced features and best practices for real-world applications.
