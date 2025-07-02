# 4b. Error Handling with ZIO

One of ZIO's most powerful features is its robust and type-safe approach to error handling. Unlike traditional Scala/Java where exceptions can be thrown at any point (leading to non-local error handling), ZIO effects explicitly declare their potential error types in their signature (`ZIO[R, E, A]`). This forces you to consider and handle errors at compile time, leading to more resilient applications.

### The `E` Type Parameter

The `E` in `ZIO[R, E, A]` represents the error channel. If an effect succeeds, it produces a value of type `A`. If it fails, it produces a value of type `E`.

*   **`E = Nothing`**: Indicates that the effect cannot fail. This is represented by `UIO[A]` or `URIO[R, A]`.
*   **`E = Throwable`**: Indicates that the effect can fail with any `java.lang.Throwable`. This is represented by `Task[A]` or `RIO[R, A]`.
*   **Custom Error ADTs**: For domain-specific errors, it's best practice to define your own sealed trait hierarchies (Algebraic Data Types) for `E`. This allows for precise and exhaustive error handling.

### Basic Error Handling Operators

ZIO provides a rich set of operators to handle, transform, and recover from errors.

*   **`ZIO.fail(error: E)`**: Creates an effect that immediately fails with the given error.

    ```scala
    val failedEffect: ZIO[Any, String, Int] = ZIO.fail("Operation failed!")
    ```

*   **`ZIO.succeed(value: A)`**: Creates an effect that immediately succeeds with the given value.

    ```scala
    val successfulEffect: ZIO[Any, Nothing, Int] = ZIO.succeed(10)
    ```

*   **`mapError(f: E => E2)`**: Transforms the error value if the effect fails.

    ```scala
    case class AppError(message: String)
    val transformedError: ZIO[Any, AppError, Int] = 
      ZIO.fail("Raw error").mapError(e => AppError(s"Transformed: $e"))
    ```

*   **`orElse(that: ZIO[R, E, A])`**: If the current effect fails, it tries to execute `that` effect.

    ```scala
    val primary: ZIO[Any, String, Int] = ZIO.fail("Primary failed")
    val fallback: ZIO[Any, String, Int] = ZIO.succeed(20)
    val result = primary.orElse(fallback) // Will succeed with 20
    ```

*   **`catchAll(f: E => ZIO[R, E2, A])`**: Recovers from any error by providing a new effect to execute.

    ```scala
    val riskyOperation: ZIO[Any, String, Int] = ZIO.fail("Network error")
    val recovered: ZIO[Any, Nothing, Int] = riskyOperation.catchAll(e => 
      ZIO.console.printLine(s"Recovering from: $e") *> ZIO.succeed(0)
    )
    ```

*   **`fold(failure: E => B, success: A => B)`**: Handles both success and failure, mapping them to a common type `B`.

    ```scala
    val outcome: ZIO[Any, Nothing, String] = riskyOperation.fold(
      error => s"Failed with: $error",
      value => s"Succeeded with: $value"
    )
    ```

*   **`ZIO.attempt(sideEffect)`**: Lifts a side-effecting computation that might throw a `Throwable` into a `Task` (`ZIO[Any, Throwable, A]`).

    ```scala
    val parseNumber: Task[Int] = ZIO.attempt("abc".toInt) // Will fail with NumberFormatException
    ```

*   **`ZIO.die(throwable: Throwable)`**: Creates an effect that dies with a `Throwable`. Dying effects are unrecoverable within ZIO's error channel and typically indicate a serious bug or unhandled exception.

    ```scala
    val dyingEffect: ZIO[Any, Nothing, Unit] = ZIO.die(new IllegalStateException("Fatal error!"))
    ```

### Error vs. Die

It's important to distinguish between an effect *failing* (`ZIO.fail`) and an effect *dying* (`ZIO.die`).

*   **Failure (`E`)**: Represents expected, recoverable errors that are part of your domain logic. You can handle these errors using operators like `catchAll`, `orElse`, `fold`, etc.
*   **Death (`Throwable`)**: Represents unexpected, unrecoverable defects or bugs in your code. These typically bypass the `E` channel and propagate up, often crashing the fiber. While you can catch defects using `ZIO.catchAllDefects`, it's generally not recommended for normal error handling.

### Example: Domain-Specific Error Handling

Let's refine the `divide` example to use a custom error ADT.

```scala
import zio._

object CustomErrorHandlingExample extends ZIOAppDefault {

  // Define a custom error ADT
  sealed trait CalculationError extends Product with Serializable
  case object DivideByZero extends CalculationError
  case class InvalidInput(message: String) extends CalculationError

  def parseAndDivide(s1: String, s2: String): ZIO[Any, CalculationError, Int] = for {
    num1 <- ZIO.attempt(s1.toInt).mapError(_ => InvalidInput(s"Cannot parse '$s1' to Int"))
    num2 <- ZIO.attempt(s2.toInt).mapError(_ => InvalidInput(s"Cannot parse '$s2' to Int"))
    result <- if (num2 == 0) ZIO.fail(DivideByZero)
              else ZIO.succeed(num1 / num2)
  } yield result

  val program: ZIO[Any, Throwable, Unit] = for {
    _ <- ZIO.console.printLine("--- Valid Division ---")
    _ <- parseAndDivide("10", "2").foldZIO(
      err => ZIO.console.printLine(s"Error: $err"),
      res => ZIO.console.printLine(s"Result: $res")
    )

    _ <- ZIO.console.printLine("\n--- Divide by Zero ---")
    _ <- parseAndDivide("10", "0").foldZIO(
      err => ZIO.console.printLine(s"Error: $err"),
      res => ZIO.console.printLine(s"Result: $res")
    )

    _ <- ZIO.console.printLine("\n--- Invalid Input ---")
    _ <- parseAndDivide("abc", "2").foldZIO(
      err => ZIO.console.printLine(s"Error: $err"),
      res => ZIO.console.printLine(s"Result: $res")
    )

    _ <- ZIO.console.printLine("\n--- Recover from Error ---")
    _ <- parseAndDivide("10", "0").catchAll {
      case DivideByZero => ZIO.console.printLine("Caught DivideByZero, returning default 0") *> ZIO.succeed(0)
      case InvalidInput(msg) => ZIO.console.printLine(s"Caught InvalidInput: $msg, returning default -1") *> ZIO.succeed(-1)
    }.foldZIO(
      err => ZIO.console.printLine(s"Unexpected error after recovery: $err"), // Should not happen
      res => ZIO.console.printLine(s"Recovered result: $res")
    )

  } yield ()

  override def run: ZIO[Any, Throwable, Unit] = program
}
```

By explicitly modeling errors as part of the `ZIO` type, you gain compile-time safety and a clear, composable way to handle all expected failure scenarios in your application.
