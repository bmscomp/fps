# 4c. Concurrency with ZIO

ZIO provides powerful and safe primitives for concurrent and parallel programming, abstracting away the complexities of threads and asynchronous operations. ZIO's concurrency model is based on **fibers**, which are lightweight, highly efficient units of execution.

### Fibers

Fibers are ZIO's equivalent of green threads or coroutines. They are much lighter than OS threads, allowing you to run millions of fibers concurrently with minimal overhead. ZIO manages the scheduling and execution of fibers on a small pool of underlying OS threads.

**Key Fiber Operations:**

*   **`fork`**: Runs an effect on a new fiber. It immediately returns a `Fiber[E, A]`, which is a handle to the running computation. The original fiber continues its execution concurrently.
*   **`join`**: Awaits the completion of a forked fiber and returns its result (either success `A` or failure `E`).
*   **`interrupt`**: Interrupts a running fiber. ZIO's interruption is safe and composable, ensuring resources are properly released.

**Example: `fork` and `join`**

```scala
import zio._
import java.io.IOException

object ForkJoinExample extends ZIOAppDefault {

  def task(name: String, duration: Duration): ZIO[Any, IOException, String] = 
    for {
      _ <- ZIO.console.printLine(s"Starting $name...")
      _ <- ZIO.sleep(duration)
      _ <- ZIO.console.printLine(s"Finished $name.")
    } yield s"Result from $name"

  val program: ZIO[Any, IOException, Unit] = for {
    fiber1 <- task("Task 1", 2.seconds).fork
    fiber2 <- task("Task 2", 3.seconds).fork
    
    _ <- ZIO.console.printLine("Tasks forked, continuing...")

    result1 <- fiber1.join
    result2 <- fiber2.join

    _ <- ZIO.console.printLine(s"\nJoined results: $result1, $result2")

  } yield ()

  override def run: ZIO[Any, Throwable, Unit] = program
}
```

### Parallel Combinators

ZIO provides several combinators for running effects in parallel.

*   **`zipPar`**: Runs two effects in parallel and combines their successful results into a tuple. If either fails, the combined effect fails.

    ```scala
    val (parResult1, parResult2) = 
      (task("Parallel Task A", 2.seconds) zipPar task("Parallel Task B", 2.seconds)).runSync // Illustrative
    // In a ZIO program:
    // (parResult1, parResult2) <- task("Parallel Task A", 2.seconds) zipPar task("Parallel Task B", 2.seconds)
    ```

*   **`race`**: Runs two effects in parallel and returns the result of the first one to complete successfully. The other fiber is interrupted.

    ```scala
    val raceResult = (task("Fast Task", 1.second) race task("Slow Task", 5.seconds)).runSync // Illustrative
    // In a ZIO program:
    // raceResult <- task("Fast Task", 1.second) race task("Slow Task", 5.seconds)
    ```

*   **`collectAllPar`**: Runs a collection of effects in parallel and collects all their successful results into a `List`.

    ```scala
    val parallelTasks = List(
      task("P-Task 1", 1.second),
      task("P-Task 2", 3.seconds),
      task("P-Task 3", 2.seconds)
    )
    val allResults <- ZIO.collectAllPar(parallelTasks)
    _ <- ZIO.console.printLine(s"\nCollected all parallel results: $allResults")
    ```

*   **`foreachPar`**: Applies an effectful function to each element of a collection in parallel.

    ```scala
    val data = List(1, 2, 3)
    val processedData <- ZIO.foreachPar(data) {
      i => ZIO.console.printLine(s"Processing item $i") *> ZIO.succeed(i * 10)
    }
    _ <- ZIO.console.printLine(s"\nProcessed data: $processedData")
    ```

### ZIO Streams (`ZStream`)

For processing sequences of data in a concurrent and resource-safe manner, ZIO provides `ZStream`. Streams are powerful for handling events, processing large datasets, or building reactive applications.

*   **`mapZIOPar`**: Processes elements of a stream in parallel, up to a specified parallelism level.

    ```scala
    import zio.stream._

    val stream = ZStream.range(1, 10)
    val processedStream = stream.mapZIOPar(5) { i => // Process 5 elements in parallel
      ZIO.console.printLine(s"Processing stream item $i") *> ZIO.sleep(1.second) *> ZIO.succeed(i * 2)
    }

    val streamProgram = for {
      _ <- ZIO.console.printLine("\n--- ZStream mapZIOPar Example ---")
      _ <- processedStream.runCollect.debug("Stream results")
    } yield ()
    // To run this: override def run: ZIO[Any, Throwable, Unit] = streamProgram
    ```

ZIO's fiber-based concurrency model and rich set of combinators make it an excellent choice for building highly concurrent and performant applications without the common pitfalls of traditional thread-based concurrency.
