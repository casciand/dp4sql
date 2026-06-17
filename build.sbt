ThisBuild / organization := ""
ThisBuild / description := "DP4SQL: Differentially Private SQL with Flexible Privacy Policies"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.6"

lazy val dpsql = (project in file("."))
  .settings(
    // calcite-core pulls protobuf-java 3.6.1, which triggers illegal reflective access warnings on Java 11+
    dependencyOverrides += "com.google.protobuf" % "protobuf-java" % "3.25.5",

    libraryDependencies ++= Seq(
      // Used for relational algebra operations
      "org.apache.calcite" % "calcite-core" % "1.19.0",
      // Used to replace deprecated MultiMap with MultiDict as recommended in deprecate doc text
      "org.scala-lang.modules" %% "scala-collection-contrib" % "0.3.0",
      // Used for SQL parsing
      "com.facebook.presto" % "presto-parser" % "0.288",
      // Used for YAML parsing
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.17.2",
      // Addon to Jackson for Scala datatypes
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.17.1",

      "org.slf4j" % "slf4j-nop" % "2.0.17",

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
    )
  )