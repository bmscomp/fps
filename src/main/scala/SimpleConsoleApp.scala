import zio._
import zio.console._
import zio.stm._
import scala.collection.immutable.HashMap

object SimpleConsoleApp extends ZIOAppDefault {

  // 1. Data Model
  case class User(id: String, name: String, email: String)

  // 2. Service Definition: UserRepo
  trait UserRepo {
    def addUser(user: User): ZIO[Any, Nothing, Unit]
    def getUser(id: String): ZIO[Any, Nothing, Option[User]]
    def getAllUsers: ZIO[Any, Nothing, List[User]]
  }

  // 3. Live Implementation: InMemoryUserRepo
  // Uses ZSTM.TRef for transactional in-memory state management
  case class InMemoryUserRepo(usersRef: TRef[HashMap[String, User]]) extends UserRepo {
    override def addUser(user: User): ZIO[Any, Nothing, Unit] = 
      usersRef.update(map => map + (user.id -> user)).commit

    override def getUser(id: String): ZIO[Any, Nothing, Option[User]] = 
      usersRef.get.map(_.get(id)).commit

    override def getAllUsers: ZIO[Any, Nothing, List[User]] = 
      usersRef.get.map(_.values.toList).commit
  }

  // 4. ZLayer for InMemoryUserRepo
  object InMemoryUserRepo {
    val live: ZLayer[Any, Nothing, UserRepo] = 
      ZLayer.fromZIO(TRef.make(HashMap.empty[String, User]).map(InMemoryUserRepo))
  }

  // 5. Application Logic
  def program: ZIO[UserRepo with Console, Throwable, Unit] = {
    def displayMenu: ZIO[Console, Nothing, Unit] = 
      putStrLn("\n--- User Management Menu ---") *> 
      putStrLn("1. Add User") *> 
      putStrLn("2. Get User by ID") *> 
      putStrLn("3. List All Users") *> 
      putStrLn("4. Exit") *> 
      putStrLn("--------------------------")

    def getUserInput(prompt: String): ZIO[Console, Throwable, String] = 
      putStr(prompt) *> getStrLn

    def addUserFlow: ZIO[UserRepo with Console, Throwable, Unit] = for {
      _ <- putStrLn("\n--- Add New User ---")
      id <- getUserInput("Enter User ID: ")
      name <- getUserInput("Enter User Name: ")
      email <- getUserInput("Enter User Email: ")
      user = User(id, name, email)
      _ <- ZIO.serviceWithZIO[UserRepo](_.addUser(user))
      _ <- putStrLn(s"User '$name' added successfully.")
    } yield ()

    def getUserFlow: ZIO[UserRepo with Console, Throwable, Unit] = for {
      _ <- putStrLn("\n--- Get User by ID ---")
      id <- getUserInput("Enter User ID to retrieve: ")
      userOpt <- ZIO.serviceWithZIO[UserRepo](_.getUser(id))
      _ <- userOpt match {
        case Some(user) => putStrLn(s"Found User: ID: ${user.id}, Name: ${user.name}, Email: ${user.email}")
        case None => putStrLn(s"User with ID '$id' not found.")
      }
    } yield ()

    def listAllUsersFlow: ZIO[UserRepo with Console, Throwable, Unit] = for {
      _ <- putStrLn("\n--- All Users ---")
      users <- ZIO.serviceWithZIO[UserRepo](_.getAllUsers)
      _ <- if (users.isEmpty) putStrLn("No users found.")
           else ZIO.foreach(users)(user => putStrLn(s"ID: ${user.id}, Name: ${user.name}, Email: ${user.email}"))
    } yield ()

    def loop: ZIO[UserRepo with Console, Throwable, Unit] = for {
      _ <- displayMenu
      choice <- getUserInput("Enter your choice: ")
      _ <- choice match {
        case "1" => addUserFlow
        case "2" => getUserFlow
        case "3" => listAllUsersFlow
        case "4" => putStrLn("Exiting application. Goodbye!") *> ZIO.succeed(Unit)
        case _ => putStrLn("Invalid choice. Please try again.")
      }
      _ <- ZIO.when(choice != "4")(loop) // Continue loop unless choice is '4'
    } yield ()

    loop
  }

  // 6. Main Application Entry Point
  override def run: ZIO[Any, Throwable, Unit] = 
    program.provide(InMemoryUserRepo.live, Console.live)
}
