name := "phouka"

version := "0.1"

scalaVersion := "2.13.2"

libraryDependencies ++= {
  Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe" % "config" % "1.3.4",
    "org.scala-lang.modules" %% "scala-collection-contrib" % "0.2.1",
    "com.intellij" % "forms_rt" % "7.0.3"
  )
}
