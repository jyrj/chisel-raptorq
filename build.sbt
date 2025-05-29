ThisBuild / scalaVersion      := "2.13.14"
ThisBuild / version           := "0.1.0"
ThisBuild / organization      := "com.jyrj" // Or your preferred organization

val chiselVersion = "3.6.1"
val chiselTestVersion = "0.6.2"

lazy val root = (project in file("."))
  .settings(
    name := "RaptorFECGen",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % chiselTestVersion % "test"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    ),
    // This line is essential for modern Chisel versions and has been uncommented.
    // It tells the Scala compiler to use the Chisel plugin.
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
  )

libraryDependencies += "org.scalatestplus" %% "junit-4-13" % "3.2.15.0" % "test"