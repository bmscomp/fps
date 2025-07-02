val scala3Version = "3.3.1"
val zioVersion = "2.0.21"
val zioJsonVersion = "0.6.0"
val zioKafkaVersion = "2.0.7"

lazy val root = project
  .in(file("."))
  .settings(
    name := "scala3-zio-workshop",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-json" % zioJsonVersion,
      "dev.zio" %% "zio-kafka" % zioKafkaVersion,
      "dev.zio" %% "zio-stm" % zioVersion,
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
