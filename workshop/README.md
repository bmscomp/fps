# Scala 3 and ZIO Workshop

Welcome to the comprehensive workshop on Scala 3 and the ZIO framework! This workshop is designed to guide you through the fundamentals of Scala 3, functional programming, and then dive deep into the powerful ZIO library for building robust, concurrent, and resilient applications.

## Workshop Structure

This workshop is divided into several modules, each focusing on a specific concept or set of related topics. We recommend going through them in order, but feel free to jump to specific sections if you're already familiar with the prerequisites.

*   [01. Introduction to Scala 3](01_introduction_to_scala3.md)
*   [02. Functional Programming Refresher](02_functional_programming_refresher.md)
*   [03. Introduction to ZIO](03_introduction_to_zio.md)
*   [04. ZIO Core Concepts](04_zio_core_concepts/04a_zio_data_type.md)
    *   [4a. The ZIO Data Type: ZIO[R, E, A]](04_zio_core_concepts/04a_zio_data_type.md)
    *   [4b. Error Handling with ZIO](04_zio_core_concepts/04b_error_handling.md)
    *   [4c. Concurrency with ZIO](04_zio_core_concepts/04c_concurrency.md)
    *   [4d. Resource Management with ZIO](04_zio_core_concepts/04d_resource_management.md)
    *   [4e. Dependency Injection with ZIO](04_zio_core_concepts/04e_dependency_injection.md)
*   [05. Practical Examples](05_practical_examples/05a_simple_console_app.md)
    *   [5a. Simple Console Application](05_practical_examples/05a_simple_console_app.md)
    *   [5b. Robust Order Processing Microservice](05_practical_examples/05b_robust_order_processing_microservice.md)
*   [06. Advanced Topics](06_advanced_topics.md)

## Getting Started

To follow along with this workshop, you'll need:

*   **Java Development Kit (JDK) 11 or newer**
*   **sbt (Scala Build Tool)**
*   **Docker (for the Kafka example)**

Clone this repository to get all the workshop materials and code examples.

```bash
git clone <repository-url>
cd <repository-name>
```

Each module will have its own markdown file with explanations and links to relevant code examples in the `src/main/scala` directory.

Enjoy your journey into Scala 3 and ZIO!
