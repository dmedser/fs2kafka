import com.scalapenos.sbt.prompt.PromptTheme

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val commonSettings =
  Seq(
    scalaVersion := "2.13.8",
    libraryDependencies ++= {

      object Version {
        val cats          = "2.7.0"
        val catsEffect    = "3.3.7"
        val fs2kafka      = "2.5.0-M3"
        val logback       = "1.2.11"
        val log4cats      = "2.2.0"
        val bm4           = "0.3.1"
        val kindProjector = "0.13.2"
      }

      val cats          = "org.typelevel"   %% "cats-core"          % Version.cats
      val catsEffect    = "org.typelevel"   %% "cats-effect"        % Version.catsEffect
      val fs2kafka      = "com.github.fd4s" %% "fs2-kafka"          % Version.fs2kafka
      val logback       = "ch.qos.logback"   % "logback-classic"    % Version.logback
      val log4cats      = "org.typelevel"   %% "log4cats-slf4j"     % Version.log4cats
      val bm4           = "com.olegpy"      %% "better-monadic-for" % Version.bm4
      val kindProjector = "org.typelevel"   %% "kind-projector"     % Version.kindProjector cross CrossVersion.full

      Seq(
        cats,
        catsEffect,
        fs2kafka,
        logback,
        log4cats
      ) ++
        Seq(
          bm4,
          kindProjector
        ).map(compilerPlugin)
    },
    resolvers ++=
      Seq(
        Resolver.sonatypeRepo("releases"),
        Resolver.bintrayRepo("krasserm", "maven")
      )
  )

lazy val root =
  project
    .in(file("."))
    .settings(name := "fs2kafka", scalaVersion := "2.13.8")

lazy val consumer =
  project
    .in(file("consumer"))
    .settings(commonSettings)
    .dependsOn(root)

lazy val producer =
  project
    .in(file("producer"))
    .settings(commonSettings)
    .dependsOn(root)

promptTheme :=
  PromptTheme(
    Seq(
      gitBranch(clean = fg(green), dirty = fg(yellow)),
      currentProject(fg(105)).padLeft(" [").padRight("] λ ")
    )
  )

addCommandAlias("deps", "dependencyUpdates")
