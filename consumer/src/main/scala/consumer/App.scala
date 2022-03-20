package consumer

import cats.data.{NonEmptyList => Nel}
import cats.effect._
import cats.syntax.traverse._
import common._
import fs2.Stream
import fs2.kafka.AutoOffsetReset

import scala.concurrent.duration._

object App extends IOApp.Simple {

  def run: IO[Unit] = program[IO]

  private def program[F[_] : Async]: F[Unit] = {

    type K = String
    type V = String

    val config =
      Config(
        bootstrapServers = BootstrapServersConfig.Default,
        topics = Nel.of(topicA, topicB, topicC),
        groupId = "group-A",
        autoOffsetReset = AutoOffsetReset.Latest,
        autoCommitEnabled = false,
        apiTimeout = 5.seconds
      )

    Consumer
      .makeResource[F, K, V](config)
      .use { consumer =>
        Stream(
          // consumer.partitionedStream.parJoinUnbounded,
          consumer.partitionsMapStream.flatMap(streamMap => Stream(streamMap.values.toSeq: _*)).parJoinUnbounded,
          consumer.assignmentStream.evalMap(_.groupBy(_.topic()).keys.toList.traverse(consumer.partitionsFor))
        ).parJoinUnbounded.compile.drain
      }
  }
}
