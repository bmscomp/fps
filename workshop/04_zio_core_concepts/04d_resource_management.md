# 4d. Resource Management with ZIO

Managing resources (like database connections, file handles, network sockets) safely is crucial to prevent leaks and ensure application stability. ZIO provides robust mechanisms for resource acquisition and release, guaranteeing that resources are properly cleaned up even in the presence of errors or interruptions.

### `ZIO.acquireRelease` and `ZIO.acquireReleaseWith`

These are fundamental primitives for managing resources. They ensure that a `release` action is performed after an `acquire` action, regardless of whether the `use` action succeeds, fails, or is interrupted.

*   **`acquire: ZIO[R, E, A]`**: An effect that acquires the resource.
*   **`release: A => ZIO[R, Nothing, Any]`**: An effect that releases the resource. It takes the acquired resource `A` as input. The error type is `Nothing` because resource release should ideally never fail.
*   **`use: A => ZIO[R2, E2, B]`**: An effect that uses the acquired resource.

**Example: File Handling**

```scala
import zio._
import java.io._
import java.nio.charset.StandardCharsets

object ResourceManagementExample extends ZIOAppDefault {

  def openFile(path: String): ZIO[Any, IOException, BufferedReader] = ZIO.attempt {
    new BufferedReader(new FileReader(path))
  }.refineToOrDie[IOException]

  def closeFile(reader: BufferedReader): ZIO[Any, Nothing, Unit] = ZIO.succeed {
    reader.close()
  }.orDie // .orDie converts Throwable to Nothing, assuming close won't fail critically

  def readFileContent(path: String): ZIO[Any, IOException, List[String]] = 
    ZIO.acquireReleaseWith(
      acquire = openFile(path),
      release = closeFile
    ) {
      reader => ZIO.attempt {
        Iterator.continually(reader.readLine()).takeWhile(_ != null).toList
      }.refineToOrDie[IOException]
    }

  val program: ZIO[Any, Throwable, Unit] = for {
    _ <- ZIO.console.printLine("--- Resource Management Example ---")
    
    // Create a dummy file for demonstration
    _ <- ZIO.attempt {
      val writer = new PrintWriter("temp_file.txt")
      writer.println("Line 1")
      writer.println("Line 2")
      writer.close()
    }.refineToOrDie[IOException]

    content <- readFileContent("temp_file.txt").catchAll {
      case e: FileNotFoundException => ZIO.console.printLine(s"File not found: ${e.getMessage}") *> ZIO.succeed(List.empty[String])
      case e: IOException => ZIO.console.printLine(s"IO Error: ${e.getMessage}") *> ZIO.succeed(List.empty[String])
    }

    _ <- ZIO.console.printLine(s"File content: $content")

    // Clean up the dummy file
    _ <- ZIO.attempt(new File("temp_file.txt").delete()).orDie
    _ <- ZIO.console.printLine("Temporary file deleted.")

  } yield ()

  override def run: ZIO[Any, Throwable, Unit] = program
}
```

### `ZManaged` (Deprecated in ZIO 2.x, but concept is important)

In ZIO 1.x, `ZManaged[R, E, A]` was a dedicated data type for modeling managed resources. While `ZManaged` is still available in ZIO 2.x, its use is often replaced by direct `ZIO.acquireReleaseWith` or by composing layers with `ZLayer` for dependency injection, which implicitly handles resource management.

**Key idea of `ZManaged` (and `acquireReleaseWith`):**

It describes a resource that can be acquired and released. It guarantees that the release action will be performed, even if an error occurs during acquisition or usage, or if the fiber is interrupted. This is crucial for preventing resource leaks.

### `ZLayer` for Resourceful Dependencies

`ZLayer` is ZIO's primary mechanism for dependency injection and also plays a vital role in resource management. When you define a `ZLayer` that provides a service, you can specify how that service (and any resources it holds) is acquired and released.

For example, a `ZLayer` for a database connection pool would acquire the pool when the layer is used and close it when the application shuts down or the layer is no longer needed.

We will cover `ZLayer` in detail in the next section (`04e_dependency_injection.md`), but it's important to understand that it builds upon the same `acquireRelease` principles to manage the lifecycle of your application's dependencies.
