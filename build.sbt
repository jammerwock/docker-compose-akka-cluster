import _root_.sbtdocker.DockerPlugin.autoImport._
import sbt._
import sbtdocker.ImageName

val scalaV = "2.11.8"
val Akka = "2.4.11"

val Version = "0.2"

name := "docker-cluster"
version := Version
scalacOptions in(Compile, console) := Seq("-feature", "-Xfatal-warnings", "-deprecation", "-unchecked")
scalaVersion := scalaV

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % Akka,
  "com.typesafe.akka" %% "akka-cluster" % Akka,
  "com.typesafe.akka" %% "akka-http-experimental" % Akka,
  "com.typesafe.akka" %% "akka-cluster-metrics" % Akka,
  "com.typesafe.akka" %% "akka-stream" % Akka,
  "com.typesafe.akka" %% "akka-slf4j" % Akka,
  "io.spray" %% "spray-json" % "1.3.2",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.13"
)

enablePlugins(sbtdocker.DockerPlugin, JavaAppPackaging)

mainClass in assembly := Some("demo.Application")

assemblyJarName in assembly := s"${name.value}-${version.value}.jar"

// Resolve duplicates for Sbt Assembly
assemblyMergeStrategy in assembly := {
  case PathList(xs @ _*) if xs.last == "io.netty.versions.properties" => MergeStrategy.rename
  case other => (assemblyMergeStrategy in assembly).value(other)
}

imageNames in docker := Seq(ImageName(namespace = Some("haghard"), repository = name.value, tag = Some(version.value)))

buildOptions in docker := BuildOptions(cache = false,
  removeIntermediateContainers = BuildOptions.Remove.Always,
  pullBaseImage = BuildOptions.Pull.Always)

dockerfile in docker := {
  val baseDir = baseDirectory.value
  val artifact: File = assembly.value

  val imageAppBaseDir = "/app"
  val configDir = "conf"

  val artifactTargetPath = s"$imageAppBaseDir/${artifact.name}"

  val seedConfigSrc = baseDir / "src" / "main" / "resources" / "seed-node.conf"
  val workerConfigSrc = baseDir / "src" / "main" / "resources" / "worker-node.conf"


  val seedConfigTarget = s"${imageAppBaseDir}/${configDir}/seed-node.conf"
  val workerConfigTarget = s"${imageAppBaseDir}/${configDir}/worker-node.conf"

  new sbtdocker.mutable.Dockerfile {
    from("openjdk:8-jre")
    maintainer("haghard")

    env("VERSION", Version)
    workDir(imageAppBaseDir)
    runRaw("ls -la")

    copy(artifact, artifactTargetPath)
    copy(seedConfigSrc, seedConfigTarget)
    copy(workerConfigSrc, workerConfigTarget)

    entryPoint("java", "-Xmx1024m", "-XX:+UseG1GC",
      s"-DseedHost=${System.getenv("SEED_NAME")}",
      s"-DseedPort=${System.getenv("AKKA_PORT")}",
      s"-DhttpPort=${System.getenv("HTTP_PORT")}",
      "-jar", artifactTargetPath)

    //"-Xmx1256M", "-XX:MaxMetaspaceSize=512m", "-XX:+HeapDumpOnOutOfMemoryError",
  }
}

//#JAVA_OPTS: "-Xmx1024m -XX:+UseG1GC -DseedHost=${SEED_NAME} -DseedPort=${AKKA_PORT} -DhttpPort=${HTTP_PORT} -Djava.rmi.server.hostname=${HOST} -Dcom.sun.management.jmxremote.port=${SEED_JMX_PORT} -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.rmi.port=${SEED_JMX_PORT} -Dcom.sun.management.jmxremote=true"
//#JAVA_OPTS: "-Xmx1024m -XX:+UseG1GC -DseedHostToConnect=${SEED_NAME} -DseedPort=${AKKA_PORT}"