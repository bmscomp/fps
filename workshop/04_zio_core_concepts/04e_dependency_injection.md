# 4e. Dependency Injection with ZIO

Dependency Injection (DI) is a software design pattern that allows for loose coupling between components. Instead of a component creating its dependencies, they are provided to it. ZIO provides a powerful and type-safe DI system based on `ZLayer`.

### The Problem with Global State and Manual Dependency Passing

In traditional programming, dependencies are often managed through global objects or by manually passing them down through many function calls. This leads to:

*   **Tight Coupling**: Components are highly dependent on specific implementations.
*   **Difficult Testing**: Hard to mock or replace dependencies for unit testing.
*   **Lack of Composability**: Hard to combine different parts of an application.
*   **Resource Leaks**: Manual resource management can lead to errors.

### `ZLayer`: The Solution

`ZLayer[RIn, E, ROut]` is ZIO's answer to dependency injection and resource management. It describes a recipe for building an environment `ROut` from an input environment `RIn`, which may fail with an error `E`.

*   **`RIn`**: The environment that the `ZLayer` *requires* to build its output.
*   **`E`**: The error type that the `ZLayer` might fail with during construction.
*   **`ROut`**: The environment (service) that the `ZLayer` *provides*.

### Core Concepts of `ZLayer`

1.  **Service Definition**: Define your services as traits.
2.  **Live Implementation**: Provide a concrete implementation of the service.
3.  **Layer Construction**: Wrap the live implementation in a `ZLayer`.
4.  **Layer Composition**: Combine `ZLayer`s to build the full application environment.
5.  **Providing the Environment**: Use `ZIO.provide` (or `ZIO.provideLayer`) to give an effect its required environment.

### Example: Simple Logging Service

Let's create a simple logging service and demonstrate how to inject it.

**1. Service Definition (Trait)**

```scala
trait Logger {
  def log(message: String): ZIO[Any, Nothing, Unit]
}
```

**2. Live Implementation (Class)**

```scala
case class ConsoleLogger(prefix: String) extends Logger {
  override def log(message: String): ZIO[Any, Nothing, Unit] = 
    ZIO.console.printLine(s"[$prefix] $message").orDie // orDie for simplicity, real logger might handle errors
}
```

**3. Layer Construction (`ZLayer` for the Live Implementation)**

We define a `ZLayer` that provides `Logger`. It requires `Any` (no input environment) and can't fail (`Nothing`).

```scala
object Logger {
  // A ZLayer that provides a Logger service
  val live: ZLayer[Any, Nothing, Logger] = 
    ZLayer.succeed(ConsoleLogger("APP"))

  // Helper accessor for the log method
  def log(message: String): ZIO[Logger, Nothing, Unit] = 
    ZIO.serviceWithZIO[Logger](_.log(message))
}
```

**4. Using the Service in an Effect**

Our effect now states that it *requires* a `Logger` in its environment (`ZIO[Logger, ..., ...]`).

```scala
val myProgram: ZIO[Logger, Nothing, Unit] = for {
  _ <- Logger.log("Application started")
  _ <- Logger.log("Performing some task...")
  _ <- Logger.log("Application finished")
} yield ()
```

**5. Providing the Environment (Wiring)**

Finally, we provide the `Logger.live` layer to `myProgram` using `provide`.

```scala
import zio._

object DependencyInjectionExample extends ZIOAppDefault {

  // 1. Service Definition
  trait Logger {
    def log(message: String): ZIO[Any, Nothing, Unit]
  }

  // 2. Live Implementation
  case class ConsoleLogger(prefix: String) extends Logger {
    override def log(message: String): ZIO[Any, Nothing, Unit] = 
      ZIO.console.printLine(s"[$prefix] $message").orDie
  }

  // 3. Layer Construction
  object Logger {
    val live: ZLayer[Any, Nothing, Logger] = 
      ZLayer.succeed(ConsoleLogger("APP"))

    // Helper accessor
    def log(message: String): ZIO[Logger, Nothing, Unit] = 
      ZIO.serviceWithZIO[Logger](_.log(message))
  }

  // 4. Using the Service
  val myProgram: ZIO[Logger, Nothing, Unit] = for {
    _ <- Logger.log("Application started")
    _ <- Logger.log("Performing some task...")
    _ <- Logger.log("Application finished")
  } yield ()

  // 5. Providing the Environment (in ZIOAppDefault's run method)
  override def run: ZIO[Any, Throwable, Unit] = 
    myProgram.provide(Logger.live)
}
```

### Layer Composition

`ZLayer`s can be composed in various ways:

*   **`>>>` (andThen)**: Sequential composition. `LayerA >>> LayerB` means `LayerA` provides `LayerB`'s input, and the result is `LayerB`'s output.
*   **`++` (and)**: Parallel composition. Combines two layers that provide different services. Their inputs are merged.
*   **`>>>` (andThen with `ZIO.service`)**: When a layer requires a service provided by another layer.

**Example: Composing Layers**

Imagine a `UserService` that requires a `Database` service, and a `Database` service that requires `Config`.

```scala
import zio._

object LayerCompositionExample extends ZIOAppDefault {

  // 1. Config Service
  case class AppConfig(dbUrl: String)
  trait ConfigService { def config: AppConfig }
  object ConfigService {
    val live: ZLayer[Any, Nothing, ConfigService] = ZLayer.succeed(new ConfigService { val config = AppConfig("jdbc:postgresql://localhost/mydb") })
    def getConfig: ZIO[ConfigService, Nothing, AppConfig] = ZIO.serviceWith[ConfigService](_.config)
  }

  // 2. Database Service (requires ConfigService)
  trait Database {
    def connect: ZIO[Any, Throwable, String]
  }
  object Database {
    val live: ZLayer[ConfigService, Throwable, Database] = ZLayer.fromFunction {
      (configService: ConfigService) => new Database {
        val dbUrl = configService.config.dbUrl
        override def connect: ZIO[Any, Throwable, String] = 
          ZIO.succeed(s"Connected to $dbUrl") // Simulate connection
      }
    }
    def connect: ZIO[Database, Throwable, String] = ZIO.serviceWithZIO[Database](_.connect)
  }

  // 3. User Service (requires Database)
  trait UserService {
    def getUser(id: Long): ZIO[Any, Throwable, String]
  }
  object UserService {
    val live: ZLayer[Database, Throwable, UserService] = ZLayer.fromFunction {
      (db: Database) => new UserService {
        override def getUser(id: Long): ZIO[Any, Throwable, String] = 
          db.connect *> ZIO.succeed(s"User-$id from DB")
      }
    }
    def getUser(id: Long): ZIO[UserService, Throwable, String] = ZIO.serviceWithZIO[UserService](_.getUser(id))
  }

  // Application Logic
  val appLogic: ZIO[UserService, Throwable, Unit] = for {
    user <- UserService.getUser(123)
    _ <- ZIO.console.printLine(s"Fetched: $user")
  } yield ()

  // Composing the layers
  // ConfigService provides ConfigService
  // Database.live requires ConfigService and provides Database
  // UserService.live requires Database and provides UserService
  val fullLayer: ZLayer[Any, Throwable, UserService] = 
    ConfigService.live >>> Database.live >>> UserService.live

  override def run: ZIO[Any, Throwable, Unit] = 
    appLogic.provide(fullLayer)
}
```

`ZLayer` is a cornerstone of building modular, testable, and maintainable ZIO applications. It ensures that dependencies are explicitly declared and correctly wired, making your application's architecture clear and robust.
