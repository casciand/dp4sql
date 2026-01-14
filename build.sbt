ThisBuild / organization := ""
ThisBuild / description := "DP4SQL: Differentially Private SQL with Flexible Privacy Policies"
ThisBuild / version := "0.1.0-SNAPSHOT"

// Scala 3.3.x is the LTS version, which will be actively maintained for at least until 2026
ThisBuild / scalaVersion := "3.3.6"

lazy val dpsql = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(
      // Used for relational algebra operations
      "org.apache.calcite" % "calcite-core" % "1.19.0",
      // Used to replace deprecated MultiMap with MultiDict as recommended in deprecate doc text
      "org.scala-lang.modules" %% "scala-collection-contrib" % "0.3.0",
      // Used for SQL parsing
      "com.facebook.presto" % "presto-parser" % "0.288", // CURRENT LATEST STABLE VERSION
      // Used for YAML parsing
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.17.2", // CURRENT LATEST STABLE VERSION
      // Addon to Jackson for Scala datatypes
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.17.1", // CURRENT LATEST VERSION
//       "ch.qos.logback" % "logback-classic" % "1.4.14",
//       "org.slf4j" % "slf4j-simple" % "2.0.13",
      "org.slf4j" % "slf4j-nop" % "2.0.17",  // Disable logging

      // Used for testing
      "org.scalatest" %% "scalatest" % "3.2.18" % Test,
      "junit" % "junit" % "4.13.2" % Test,
      "com.novocode" % "junit-interface" % "0.11" % Test
    ),

    scalacOptions ++= Seq(
      "-source:3.0-migration",
      "-rewrite",
      "encoding",
      "utf8",
      "-unchecked",
      "-deprecation",
//      "-feature",
//      "-language:experimental.macros",
//      "-language:higherKinds",
//      "-language:implicitConversions",
//      "-Wvalue-discard",
//      "-Wunused:implicits",
//      "-Wunused:explicits",
//      "-Wunused:imports",
//      "-Wunused:locals",
//      "-Wunused:params",
//      "-Wunused:privates",
//      "-Xfatal-warnings"
    )
  )