# Scala 3 and ZIO Framework Workshop for Engineering Students

## Table of Contents

1.  [Introduction to Scala 3](#1-introduction-to-scala-3)
2.  [Functional Programming Refresher](#2-functional-programming-refresher)
3.  [Introduction to ZIO](#3-introduction-to-zio)
4.  [ZIO Core Concepts](#4-zio-core-concepts)
    *   [The ZIO Data Type: ZIO[R, E, A]](#the-zio-data-type-zio-r-e-a)
    *   [Error Handling with ZIO](#error-handling-with-zio)
    *   [Concurrency with ZIO](#concurrency-with-zio)
    *   [Resource Management with ZIO](#resource-management-with-zio)
    *   [Dependency Injection with ZIO](#dependency-injection-with-zio)
5.  [Practical Examples](#5-practical-examples)
6.  [Advanced Topics (Briefly)](#6-advanced-topics-briefly)

---

## 1. Introduction to Scala 3

Scala 3 (formerly Dotty) is the latest major revision of the Scala programming language. It brings significant improvements in terms of syntax, type system, and tooling, making Scala even more powerful and enjoyable to use. Scala is a multi-paradigm programming language designed to integrate features of object-oriented and functional programming.

### Key Features and Improvements in Scala 3:

*   **New Syntax**: More readable and less ceremonial syntax, especially for control structures (e.g., `if`, `for`, `while`) and enums.
*   **Enums**: First-class support for enums, which can also be parameterized and have methods.
*   **Intersection and Union Types**: Powerful new type system features for more expressive and flexible type definitions.
*   **Opaque Type Aliases**: Allows creating new types that are type-checked as distinct from their underlying type, but compile to the underlying type, avoiding runtime overhead.
*   **Contextual Abstractions (Implicit Redesign)**: A more principled and explicit way to handle implicits, now called "contextual abstractions," including `given` and `using` clauses.
*   **Extension Methods**: A cleaner way to add methods to existing types without modifying their original definition.
*   **Metaprogramming**: Improved metaprogramming capabilities with a new Macro system.
*   **Trait Parameters**: Traits can now take parameters, making them more flexible and powerful.

### Why Scala 3?

Scala 3 aims to be:

*   **More Expressive**: With new syntax and type system features.
*   **Safer**: Stronger type system helps catch more errors at compile time.
*   **More Performant**: Compiler optimizations and new language features can lead to more efficient code.
*   **Easier to Learn**: Simplified syntax and clearer concepts.

### Functional Programming in Scala

Scala is a hybrid language, but it strongly encourages a functional programming style. Functional programming (FP) is a programming paradigm where programs are constructed by applying and composing functions. It is a declarative programming paradigm, meaning programming is done with expressions or declarations instead of statements.

**Core Principles of FP:**

*   **Immutability**: Data structures are not modified after creation.
*   **Pure Functions**: Functions that, given the same input, will always return the same output and produce no side effects.
*   **First-Class Functions**: Functions can be treated as values (passed as arguments, returned from other functions, assigned to variables).
*   **Higher-Order Functions (HOFs)**: Functions that take other functions as arguments or return functions as results.

---

## 2. Functional Programming Refresher

Before diving deep into ZIO, let's quickly recap some fundamental functional programming concepts that are crucial for understanding ZIO.

### Immutability

In functional programming, data is immutable. Once a data structure is created, it cannot be changed. Instead of modifying existing data, you create new data structures with the desired changes.

**Example (Scala):**

```scala
val originalList = List(1, 2, 3)
val newList = originalList :+ 4 // Creates a new list (1, 2, 3, 4), originalList remains unchanged

println(s"Original List: $originalList")
println(s"New List: $newList")
```

### Pure Functions

A pure function is a function that satisfies two conditions:

1.  **Deterministic**: Given the same input, it always returns the same output.
2.  **No Side Effects**: It does not cause any observable changes outside its local environment (e.g., modifying mutable data, performing I/O, throwing exceptions).

**Example (Scala):**

```scala
def add(a: Int, b: Int): Int = a + b // Pure function

var counter = 0
def incrementAndGet(): Int = { // Impure function (modifies external state)
  counter += 1
  counter
}

println(s"Add: ${add(2, 3)}")
println(s"Increment 1: ${incrementAndGet()}")
println(s"Increment 2: ${incrementAndGet()}")
```

### Higher-Order Functions (HOFs)

HOFs are functions that either take one or more functions as arguments or return a function as their result. They are a powerful abstraction mechanism in functional programming.

**Common HOFs in Scala Collections:**

*   `map`: Transforms each element of a collection.
*   `filter`: Selects elements based on a predicate.
*   `fold`/`reduce`: Combines elements into a single result.

**Example (Scala):**

```scala
val numbers = List(1, 2, 3, 4, 5)

// Using map to double each number
val doubledNumbers = numbers.map(x => x * 2)
println(s"Doubled Numbers: $doubledNumbers")

// Using filter to get even numbers
val evenNumbers = numbers.filter(_ % 2 == 0)
println(s"Even Numbers: $evenNumbers")

// Using foldLeft to sum numbers
val sum = numbers.foldLeft(0)(_ + _)
println(s"Sum: $sum")
```

### Type Classes

Type classes are a powerful concept in functional programming that allows you to add new behavior to existing types without modifying their original definition. They are a way to achieve ad-hoc polymorphism.

In Scala, type classes are typically implemented using traits and implicit (now `given`/`using`) parameters.

**Example (Scala - simplified `Show` type class):**

```scala
trait Show[A] {
  def show(value: A): String
}

object Show {
  def apply[A](implicit s: Show[A]): Show[A] = s

  implicit val intShow: Show[Int] = new Show[Int] {
    def show(value: Int): String = s"Int($value)"
  }

  implicit val stringShow: Show[String] = new Show[String] {
    def show(value: String): String = s"String("$value")"
  }
}

// Extension method to make it more ergonomic
extension class ShowOps[A](value: A)(using s: Show[A]) {
  def show: String = s.show(value)
}

println(123.show)
println("hello".show)
```

---

## 3. Introduction to ZIO

ZIO is a powerful, purely functional library for asynchronous and concurrent programming in Scala. It provides a robust, type-safe, and performant way to build complex applications that deal with effects (side effects).

### What is ZIO?

At its core, ZIO is about managing **effects**. In functional programming, an "effect" is any computation that interacts with the outside world (e.g., reading from a database, making an HTTP request, printing to the console, generating a random number). These are often called "side effects" because they change the state of the world outside the function's local scope.

ZIO provides a data type, `ZIO[R, E, A]`, that represents a description of a computation that:

*   **Requires an environment `R`**: A set of services or dependencies it needs to run.
*   **May fail with an error `E`**: The type of error it can produce.
*   **May succeed with a value `A`**: The type of value it can produce upon success.

This `ZIO` data type is a **description** of a computation, not the computation itself. This means that ZIO effects are **lazy** and **immutable**. They don't run until explicitly `run` or `provide` with an environment.

### Core Principles of ZIO:

1.  **Purely Functional**: ZIO encourages writing pure functions and manages side effects explicitly through its `ZIO` data type.
2.  **Type-Safe**: Errors, environments, and success values are all encoded in the type signature, allowing the compiler to catch many common mistakes.
3.  **Asynchronous and Concurrent**: Built from the ground up for highly concurrent and performant applications, leveraging lightweight fibers instead of traditional threads.
4.  **Resource-Safe**: Provides powerful mechanisms (`ZManaged`, `ZLayer`) for safe resource acquisition and release, preventing resource leaks.
5.  **Testable**: Purely functional nature and explicit dependency management make ZIO applications highly testable.

### Why use ZIO?

*   **Eliminates Callback Hell**: ZIO's monadic structure allows you to compose asynchronous operations sequentially and declaratively, avoiding nested callbacks.
*   **Robust Error Handling**: Explicit and powerful error handling mechanisms that make it difficult to ignore errors.
*   **Concurrency Made Easy**: Simple and safe primitives for parallel and concurrent execution.
*   **Dependency Management**: `ZLayer` provides a robust, type-safe, and composable way to manage application dependencies.
*   **Testability**: The pure and modular design makes testing straightforward.

---

## 4. ZIO Core Concepts

Let's dive into the fundamental building blocks of ZIO.

### The ZIO Data Type: `ZIO[R, E, A]`

As mentioned, `ZIO[R, E, A]` describes a computation that:

*   `R` (Environment): The type of environment (dependencies) required by the effect.
*   `E` (Error): The type of error that the effect can fail with.
*   `A` (Success): The type of value that the effect can succeed with.

If an effect doesn't require an environment, `R` is `Any`. If it cannot fail, `E` is `Nothing`. If it doesn't produce a value, `A` is `Unit`.

**Common ZIO Constructors:**

*   `ZIO.succeed(value)`: Creates an effect that immediately succeeds with `value`. Type: `ZIO[Any, Nothing, A]`
*   `ZIO.fail(error)`: Creates an effect that immediately fails with `error`. Type: `ZIO[Any, E, Nothing]`
*   `ZIO.attempt(sideEffect)`: Captures a synchronous side effect that might throw an exception. Type: `ZIO[Any, Throwable, A]`
*   `ZIO.console.printLine(message)`: An effect that prints a line to the console. Type: `ZIO[Any, IOException, Unit]`

**Composing ZIO Effects:**

ZIO effects are monads, which means you can compose them using `map`, `flatMap`, and `for-comprehensions`.

**Example:**

```scala
import zio._

object BasicZIOExample extends ZIOAppDefault {

  val program: ZIO[Any, Throwable, Unit] = for {
    _ <- ZIO.console.printLine("What is your name?")
    name <- ZIO.console.readLine
    _ <- ZIO.console.printLine(s"Hello, $name!")
  } yield ()

  override def run: ZIO[Any, Throwable, Unit] = program
}
```

### Error Handling with ZIO

ZIO provides a robust and explicit way to handle errors. Unlike traditional `try-catch` blocks, ZIO errors are part of the type signature, forcing you to acknowledge and handle them.

*   **`E` type parameter**: Represents the type of error that can occur.
*   **`mapError`**: Transforms the error type.
*   **`orElse`**: Provides a fallback effect if the current one fails.
*   **`catchAll`**: Recovers from any error by providing a new effect.
*   **`fold`**: Handles both success and failure cases.

**Example:**

```scala
import zio._
import java.io.IOException

object ErrorHandlingExample extends ZIOAppDefault {

  def readFile(filename: String): ZIO[Any, IOException, String] = 
    ZIO.attempt { 
      if (filename == "data.txt") "File content" 
      else throw new IOException("File not found") 
    }.mapError { 
      case e: IOException => e 
      case other => new IOException(s"Unexpected error: ${other.getMessage}") 
    }

  val program: ZIO[Any, Nothing, Unit] = for {
    _ <- ZIO.console.printLine("Attempting to read data.txt...")
    content1 <- readFile("data.txt").catchAll { e =>
      ZIO.console.printLine(s"Error reading data.txt: ${e.getMessage}").as("Default Content")
    }
    _ <- ZIO.console.printLine(s"Content 1: $content1")

    _ <- ZIO.console.printLine("\nAttempting to read non_existent.txt...")
    content2 <- readFile("non_existent.txt").fold(
      error => s"Failed to read: ${error.getMessage}",
      success => s"Successfully read: $success"
    )
    _ <- ZIO.console.printLine(s"Content 2: $content2")

  } yield ()

  override def run: ZIO[Any, Throwable, Unit] = program
}
```

### Concurrency with ZIO

ZIO's concurrency model is built around **Fibers**. Fibers are lightweight, highly efficient units of execution that are managed by the ZIO runtime. They are similar to green threads but are purely functional and provide powerful composition primitives.

*   **`fork`**: Runs an effect on a new fiber, returning a `Fiber[E, A]`.
*   **`join`**: Waits for a fiber to complete and retrieves its result.
*   **`race`**: Runs two effects in parallel and returns the result of the first one to complete.
*   **`zipPar`**: Runs two effects in parallel and combines their successful results into a tuple.
*   **`ZIO.foreachPar`**: Runs a collection of effects in parallel.

**Example:**

```scala
import zio._
import java.io.IOException

object ConcurrencyExample extends ZIOAppDefault {

  def task(name: String, duration: Duration): ZIO[Any, IOException, String] = 
    ZIO.console.printLine(s"Starting $name...") *> 
    ZIO.sleep(duration) *> 
    ZIO.console.printLine(s"Finished $name.").as(s"Result from $name")

  val program: ZIO[Any, Throwable, Unit] = for {
    fiber1 <- task("Task 1", 2.seconds).fork
    fiber2 <- task("Task 2", 3.seconds).fork
    
    _ <- ZIO.console.printLine("Tasks forked, continuing...")

    result1 <- fiber1.join
    result2 <- fiber2.join

    _ <- ZIO.console.printLine(s"\nJoined results: $result1, $result2")

    raceResult <- task("Fast Task", 1.second).race(task("Slow Task", 5.seconds))
    _ <- ZIO.console.printLine(s"\nRace result: $raceResult")

    (parResult1, parResult2) <- task("Parallel Task A", 2.seconds) zipPar task("Parallel Task B", 2.seconds)
    _ <- ZIO.console.printLine(s"\nZipPar results: $parResult1, $parResult2")

  } yield ()

  override def run: ZIO[Any, Throwable, Unit] = program
}
```

### Resource Management with ZIO

Managing resources (like database connections, file handles, network sockets) safely is crucial to prevent leaks. ZIO provides `ZManaged` and `ZLayer` for robust resource acquisition and release.

*   **`ZManaged[R, E, A]`**: Describes a resource that can be acquired and released. It guarantees that the release action will be performed, even if an error occurs during acquisition or usage.

**Example (ZManaged):**

```scala
import zio._
import java.io.IOException

object ZManagedExample extends ZIOAppDefault {

  case class Connection(id: Int) {
    def query(sql: String): ZIO[Any, IOException, String] = 
      ZIO.console.printLine(s"Connection $id: Executing query '$sql'").as(s"Data from $id for $sql")
    def close(): ZIO[Any, Nothing, Unit] = 
      ZIO.console.printLine(s"Connection $id: Closing...").unit
  }

  def acquireConnection(id: Int): ZIO[Any, IOException, Connection] = 
    ZIO.console.printLine(s"Acquiring Connection $id...").as(Connection(id))

  val managedConnection: ZManaged[Any, IOException, Connection] = 
    ZManaged.acquireReleaseWith(
      acquire = acquireConnection(1)
    )(
      release = _.close()
    )

  val program: ZIO[Any, Throwable, Unit] = for {
    _ <- ZIO.console.printLine("Using ZManaged for connection...")
    data <- managedConnection.use { conn =>
      conn.query("SELECT * FROM users")
    }
    _ <- ZIO.console.printLine(s"Received data: $data")
  } yield ()

  override def run: ZIO[Any, Throwable, Unit] = program
}
```

### Dependency Injection with ZIO

ZIO's `ZLayer` provides a powerful and type-safe way to manage dependencies in your application. It allows you to define services as layers that can be composed and provided to your effects.

*   **`ZLayer[RIn, E, ROut]`**: Describes a layer that takes an environment `RIn` and produces an environment `ROut` (a service).

**Example (ZLayer - simplified logging service):**

```scala
import zio._

// 1. Define the service interface
trait Logger {
  def log(message: String): ZIO[Any, Nothing, Unit]
}

// 2. Define the service implementation
case class ConsoleLogger() extends Logger {
  override def log(message: String): ZIO[Any, Nothing, Unit] = 
    ZIO.console.printLine(s"[LOG] $message").orDie // .orDie converts IOException to FiberFailure
}

// 3. Define the ZLayer that provides the service
object ConsoleLogger {
  val live: ZLayer[Any, Nothing, Logger] = ZLayer.succeed(ConsoleLogger())
}

// 4. Define a module that requires the service
object MyService {
  def doSomething: ZIO[Logger, Nothing, Unit] = 
    ZIO.service[Logger].flatMap(_.log("Doing something important..."))
}

// 5. Run the application, providing the required layer
object ZLayerExample extends ZIOAppDefault {
  override def run: ZIO[Any, Throwable, Unit] = 
    MyService.doSomething.provide(ConsoleLogger.live)
}
```

---

## 5. Practical Examples

Let's put these concepts into practice with a more complete example.

### Example: Simple User Management CLI Application

This example will demonstrate reading input, handling errors, and managing a simple in-memory user store.

```scala
// project/build.sbt
// scalaVersion := "3.3.1"
// libraryDependencies += "dev.zio" %% "zio" % "2.0.21"
// libraryDependencies += "dev.zio" %% "zio-test" % "2.0.21" % Test
// libraryDependencies += "dev.zio" %% "zio-test-sbt" % "2.0.21" % Test

import zio._
import java.io.IOException
import scala.collection.mutable

// 1. Define the User case class
case class User(id: String, name: String, email: String)

// 2. Define the UserRepo service interface
trait UserRepo {
  def addUser(user: User): ZIO[Any, Nothing, Unit]
  def getUser(id: String): ZIO[Any, Nothing, Option[User]]
  def getAllUsers: ZIO[Any, Nothing, List[User]]
}

// 3. Implement the UserRepo service (in-memory for simplicity)
case class InMemoryUserRepo(users: mutable.Map[String, User]) extends UserRepo {
  override def addUser(user: User): ZIO[Any, Nothing, Unit] = 
    ZIO.succeed(users.put(user.id, user)).unit

  override def getUser(id: String): ZIO[Any, Nothing, Option[User]] = 
    ZIO.succeed(users.get(id))

  override def getAllUsers: ZIO[Any, Nothing, List[User]] = 
    ZIO.succeed(users.values.toList)
}

// 4. Define the ZLayer for InMemoryUserRepo
object InMemoryUserRepo {
  val live: ZLayer[Any, Nothing, UserRepo] = 
    ZLayer.succeed(InMemoryUserRepo(mutable.Map.empty[String, User]))
}

// 5. Define the CLI application logic
object UserCli extends ZIOAppDefault {

  def program: ZIO[UserRepo, IOException, Unit] = for {
    _ <- ZIO.console.printLine("\n--- User Management CLI ---")
    _ <- ZIO.console.printLine("Commands: add, get, list, exit")
    _ <- loop
  } yield ()

  def loop: ZIO[UserRepo, IOException, Unit] = 
    for {
      _ <- ZIO.console.print("Enter command: ")
      command <- ZIO.console.readLine.map(_.trim.toLowerCase)
      _ <- command match {
        case "add" => addUserFlow
        case "get" => getUserFlow
        case "list" => listUsersFlow
        case "exit" => ZIO.console.printLine("Exiting...")
        case _ => ZIO.console.printLine("Unknown command.")
      }
      _ <- ZIO.when(command != "exit")(loop) // Continue loop unless command is exit
    } yield ()

  def addUserFlow: ZIO[UserRepo, IOException, Unit] = 
    for {
      _ <- ZIO.console.print("Enter user ID: ")
      id <- ZIO.console.readLine
      _ <- ZIO.console.print("Enter user name: ")
      name <- ZIO.console.readLine
      _ <- ZIO.console.print("Enter user email: ")
      email <- ZIO.console.readLine
      user = User(id, name, email)
      _ <- ZIO.service[UserRepo].flatMap(_.addUser(user))
      _ <- ZIO.console.printLine(s"User $name added.")
    } yield ()

  def getUserFlow: ZIO[UserRepo, IOException, Unit] = 
    for {
      _ <- ZIO.console.print("Enter user ID to get: ")
      id <- ZIO.console.readLine
      maybeUser <- ZIO.service[UserRepo].flatMap(_.getUser(id))
      _ <- maybeUser match {
        case Some(user) => ZIO.console.printLine(s"Found user: $user")
        case None => ZIO.console.printLine(s"User with ID $id not found.")
      }
    } yield ()

  def listUsersFlow: ZIO[UserRepo, IOException, Unit] = 
    for {
      users <- ZIO.service[UserRepo].flatMap(_.getAllUsers)
      _ <- ZIO.console.printLine("--- All Users ---")
      _ <- ZIO.foreach(users)(user => ZIO.console.printLine(s"  - $user"))
      _ <- ZIO.console.printLine("-----------------")
    } yield ()

  override def run: ZIO[Any, Throwable, Unit] = 
    program.provide(InMemoryUserRepo.live)
}
```

**To run this example:**

1.  Create a new Scala 3 project (e.g., using `sbt new scala/scala3.g8` or `sbt new scala/zio-project.g8`).
2.  Add the ZIO dependencies to your `build.sbt` file as commented in the code.
3.  Place the code in a file like `src/main/scala/UserCli.scala`.
4.  Run `sbt run` in your terminal.

---

## 6. Advanced Topics (Briefly)

This workshop provides a foundation, but ZIO offers much more:

*   **ZIO Streams**: For building reactive and concurrent data pipelines.
*   **ZIO Test**: A powerful testing framework built on ZIO, enabling purely functional tests.
*   **ZIO HTTP**: A high-performance, purely functional HTTP library for building web services.
*   **ZIO Kafka, ZIO Quill, ZIO Redis**: Integrations with various data stores and messaging systems.
*   **ZIO Actors**: A purely functional actor system.

These topics can be explored in more depth in future workshops or by consulting the official ZIO documentation.

---

## Conclusion

Scala 3 and the ZIO framework provide a robust and elegant solution for building modern, concurrent, and resilient applications. By embracing functional programming principles and leveraging ZIO's powerful abstractions, you can write expressive, type-safe, and highly testable code. We encourage you to continue exploring the ZIO ecosystem and building your own purely functional applications!

---

## Resources

*   **Scala 3 Documentation**: [https://docs.scala-lang.org/scala3/](https://docs.scala-lang.org/scala3/)
*   **ZIO Official Website**: [https://zio.dev/](https://zio.dev/)
*   **ZIO on GitHub**: [https://github.com/zio/zio](https://github.com/zio/zio)
*   **Functional Programming in Scala (Red Book)**: A classic resource for understanding FP concepts.
*   **Typelevel Libraries**: Explore other functional programming libraries in the Scala ecosystem.

