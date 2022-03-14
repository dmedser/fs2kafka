package consumer

import cats.Show
import cats.effect.kernel.Concurrent
import cats.effect.syntax.resource._
import cats.effect.{Async, Resource}
import cats.syntax.apply._
import cats.syntax.foldable._
import cats.syntax.option._
import cats.syntax.show._
import fs2.Stream
import fs2.kafka._
import org.apache.kafka.common.TopicPartition
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait Consumer[F[_]] {
  def stream: Stream[F, Unit]
  def partitionedStream: Stream[F, Unit]
  def partitionsMapStream: Stream[F, Unit]
}

object Consumer {

  def makeResource[F[_] : Async, K : Show, V : Show](
      config: Config
  )(implicit keyDeserializer: Deserializer[F, K], valueDeserializer: Deserializer[F, V]): Resource[F, Consumer[F]] =
    for {
      consumer <-
        KafkaConsumer
          .resource(
            ConsumerSettings[F, K, V]
              .withBootstrapServers(config.bootstrapServers.toString)
              .withGroupId(config.groupId)
              .withAutoOffsetReset(config.autoOffsetReset)
              .withEnableAutoCommit(config.autoCommitEnabled)
          )
          .evalTap(_.subscribeTo(config.topics.head, config.topics.tail: _*))
      log <- Slf4jLogger.create.toResource
    } yield new Impl(consumer, log)

  private final class Impl[F[_] : Concurrent, K : Show, V : Show](
      consumer: KafkaConsumer[F, K, V],
      log: Logger[F]
  ) extends Consumer[F] {

    private def processRecord(
        record: CommittableConsumerRecord[F, K, V],
        partition: Option[TopicPartition] = None
    ): F[Unit] =
      log.info {
        "New message received: " +
          partition.foldMap(x => s"partition = $x, ") +
          s"key = '${record.record.key.show}', " +
          s"value = '${record.record.value.show}'"
      } *> record.offset.commit

    def stream: Stream[F, Unit] =
      consumer.stream.evalMap(processRecord(_))

    def partitionedStream: Stream[F, Unit] =
      consumer.partitionedStream.map {
        _.evalMap(processRecord(_))
      }.parJoinUnbounded

    def partitionsMapStream: Stream[F, Unit] =
      consumer.partitionsMapStream.flatMap { streamsMap =>
        Stream(
          streamsMap.map { case (partition, stream) =>
            stream.evalMap(processRecord(_, partition.some))
          }.toSeq: _*
        ).parJoinUnbounded
      }
  }
}
