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

val teavmTooling = "org.teavm" % "teavm-tooling" % "0.9.2" excludeAll (
  Seq(
    "commons-io",
    "com.carrotsearch",
    "org.mozilla",
    "org.ow2.asm",
  ).map(x => ExclusionRule(organization = x))*
)

libraryDependencies ++= Seq(
  teavmTooling,
  "ws.unfiltered" %% "unfiltered-filter" % "0.12.0",
  "ws.unfiltered" %% "unfiltered-jetty" % "0.12.0",
)

scalacOptions ++= Seq(
  "-deprecation",
  "-Ywarn-unused:imports",
)

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

publishTo := sonatypePublishToBundle.value

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
    enableCrossBuild = false
  ),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges,
)

sbtPluginPublishLegacyMavenStyle := false

enablePlugins(ContrabandPlugin)

Compile / generateContrabands / contrabandScalaArray := "Seq"

Compile / packageSrc / mappings ++= (Compile / managedSources).value.map { f =>
  // to merge generated sources into sources.jar as well
  (f, f.relativeTo((Compile / sourceManaged).value).get.getPath)
}

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
