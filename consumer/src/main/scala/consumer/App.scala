package consumer

import cats.data.{NonEmptyList => Nel}
import cats.effect._
import common.{BootstrapServersConfig, defaults}
import fs2.kafka.AutoOffsetReset

object App extends IOApp.Simple {

  def run: IO[Unit] = program[IO]

  private def program[F[_] : Async]: F[Unit] = {

    type K = String
    type V = String

    val config =
      Config(
        bootstrapServers = BootstrapServersConfig.Default,
        topics = Nel.one(defaults.topic),
        groupId = "test-group",
        autoOffsetReset = AutoOffsetReset.Earliest,
        autoCommitEnabled = false
      )

    Consumer
      .makeResource[F, K, V](config)
      .use(_.partitionsMapStream.compile.drain) // TODO graceful shutdown
  }
}
