package consumer

import cats.Applicative
import cats.data.{NonEmptyList => Nel}
import cats.effect._
import cats.effect.std.Console
import cats.syntax.flatMap._
import cats.syntax.functor._
import common._
import fs2.Stream
import fs2.kafka.AutoOffsetReset
import org.apache.kafka.common.TopicPartition

import scala.concurrent.duration._

object App extends IOApp.Simple {

  def run: IO[Unit] = program[IO]

  private def program[F[_] : Async](implicit console: Console[F]): F[Unit] = {

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

    def commandsStream(consumer: Consumer[F, K, V]): Stream[F, Unit] = {

      def makePartition(topic: String, partition: String): TopicPartition =
        new TopicPartition(s"topic-${topic.toUpperCase}", partition.toInt)

      def nextCommand: F[Unit] =
        for {
          cmd <- console.readLine
          _ <- cmd match {
            case s"pos $t $p"     => consumer.position(makePartition(t, p))
            case s"seek $t $p $o" => consumer.seek(makePartition(t, p), o.toLong)
            case s"commit $t $p"  => consumer.committed(Set(makePartition(t, p)))
            case _                => Applicative[F].unit
          }
        } yield ()

      Stream.eval(nextCommand).repeat
    }

    Consumer
      .makeResource[F, K, V](config)
      .use { consumer =>
        Stream(
          // consumer.partitionedStream.parJoinUnbounded,
          consumer.partitionsMapStream.flatMap { streamMap =>
            Stream(streamMap.values.toSeq: _*)
          }.parJoinUnbounded,
          commandsStream(consumer)
          /*consumer.assignmentStream
            .evalMap { partitions =>
              val partitionsGroupedByTopic = partitions.groupBy(_.topic()).toList

              for {
                _ <- partitionsGroupedByTopic.traverse { case (topic, _) => consumer.partitionsFor(topic) }
                _ <- partitionsGroupedByTopic.traverse { case (_, partitions) => consumer.beginningOffsets(partitions) }
                _ <- partitionsGroupedByTopic.traverse { case (_, partitions) => consumer.endOffsets(partitions) }
              } yield ()
            }*/
        ).parJoinUnbounded.compile.drain
      }
  }
}
