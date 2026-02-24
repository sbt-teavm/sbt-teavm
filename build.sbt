name := "sbt-teavm"

organization := "io.github.sbt-teavm"

scriptedBufferLog := false

scriptedLaunchOpts ++= {
  val javaVmArgs = {
    import scala.collection.JavaConverters._
    java.lang.management.ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toList
  }
  javaVmArgs.filter(a => Seq("-Xmx", "-Xms", "-XX", "-Dsbt.log.noformat").exists(a.startsWith))
}

scriptedLaunchOpts ++= Seq[(String, String)](
  "plugin.version" -> version.value,
).map { case (k, v) =>
  s"-D${k}=${v}"
}

enablePlugins(SbtPlugin)

val teavmTooling = "org.teavm" % "teavm-tooling" % "0.13.1" excludeAll (
  Seq(
    "commons-io",
    "com.carrotsearch",
    "org.mozilla",
    "org.ow2.asm",
  ).map(x => ExclusionRule(organization = x)) *
)

libraryDependencies ++= Seq(
  teavmTooling,
  "ws.unfiltered" %% "unfiltered-filter" % "0.12.1",
  "ws.unfiltered" %% "unfiltered-jetty" % "0.12.1",
)

scalacOptions ++= Seq(
  "-deprecation",
  "-Wconf:origin=sbtteavm.SbtTeaVMCompat:silent",
)

val unusedWarnings = Def.setting(
  scalaBinaryVersion.value match {
    case "2.12" =>
      Seq("-Ywarn-unused:imports")
    case _ =>
      Seq("-Wunused:imports")
  }
)

scalacOptions ++= unusedWarnings.value

Seq(Compile, Test).flatMap(c => c / console / scalacOptions --= unusedWarnings.value)

scalafixOnCompile := true
semanticdbEnabled := true

Compile / sourceGenerators += Def.task {
  val objName = "SbtTeaVMBuildInfo"
  val f = (Compile / sourceManaged).value / "sbtteavm" / s"${objName}.scala"
  val src =
    s"""package sbtteavm
       |
       |private[sbtteavm] object ${objName} {
       |  def teavmVersion: String = "${teavmTooling.revision}"
       |}
       |""".stripMargin
  IO.write(f, src)
  Seq(f)
}

val tagName = Def.setting {
  s"v${if (releaseUseGlobalVersion.value) (ThisBuild / version).value else version.value}"
}

val tagOrHash = Def.setting {
  if (isSnapshot.value) sys.process.Process("git rev-parse HEAD").lineStream_!.head
  else tagName.value
}

pomExtra := {
  <developers>
    <developer>
      <id>xuwei-k</id>
      <name>Kenji Yoshida</name>
      <url>https://github.com/xuwei-k</url>
    </developer>
  </developers>
  <scm>
    <url>git@github.com:sbt-teavm/sbt-teavm.git</url>
    <connection>scm:git:git@github.com:sbt-teavm/sbt-teavm.git</connection>
    <tag>{tagOrHash.value}</tag>
  </scm>
}

homepage := Some(url("https://github.com/sbt-teavm/sbt-teavm"))

licenses := Seq("MIT License" -> url("https://opensource.org/license/mit/"))

releaseTagName := tagName.value

publishTo := (if (isSnapshot.value) None else localStaging.value)

Compile / doc / scalacOptions ++= {
  val tag = tagOrHash.value
  Seq(
    "-sourcepath",
    (LocalRootProject / baseDirectory).value.getAbsolutePath,
    "-doc-source-url",
    s"https://github.com/sbt-teavm/sbt-teavm/tree/${tag}€{FILE_PATH}.scala",
  )
}

import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(
    action = { state =>
      val extracted = Project extract state
      extracted.runAggregated(extracted.get(thisProjectRef) / (Global / PgpKeys.publishSigned), state)
    },
    enableCrossBuild = true
  ),
  releaseStepCommand("sonaRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges,
)

sbtPluginPublishLegacyMavenStyle := false

enablePlugins(ContrabandPlugin)

Compile / generateContrabands / contrabandScalaArray := "Seq"

pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" =>
      (pluginCrossBuild / sbtVersion).value
    case _ =>
      "2.0.0-RC9"
  }
}

crossScalaVersions += "3.8.1"
