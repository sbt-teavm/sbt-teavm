addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.1")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.5.0")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.9.0")

if (sys.env.isDefinedAt("GITHUB_ACTION")) {
  Def.settings(
    addSbtPlugin("net.virtual-void" % "sbt-hackers-digest" % "0.1.2")
  )
} else {
  Nil
}

libraryDependencies += "com.github.xuwei-k" %% "scala-version-from-sbt-version" % "0.1.0"
