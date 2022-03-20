package consumer

import cats.Show
import cats.effect.kernel.Concurrent
import cats.effect.syntax.resource._
import cats.effect.{Async, Resource}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.option._
import cats.syntax.show._
import fs2.Stream
import fs2.kafka._
import org.apache.kafka.common.{PartitionInfo, TopicPartition}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.collection.immutable.SortedSet

trait Consumer[F[_], K, V] extends Consume[F, K, V] with Assignment[F] with Topics[F]

trait Consume[F[_], K, V] {
  def partitionedStream: Stream[F, Stream[F, CommittableConsumerRecord[F, K, V]]]
  def partitionsMapStream: Stream[F, Map[TopicPartition, Stream[F, CommittableConsumerRecord[F, K, V]]]]
}

trait Assignment[F[_]] {
  def assignmentStream: Stream[F, SortedSet[TopicPartition]]
}

trait Topics[F[_]] {
  def partitionsFor(topic: String): F[List[PartitionInfo]]
}

object Consumer {

  def makeResource[F[_] : Async, K : Show, V : Show](
      config: Config
  )(implicit keyDeserializer: Deserializer[F, K], valueDeserializer: Deserializer[F, V]): Resource[F, Consumer[F, K, V]] =
    for {
      consumer <-
        KafkaConsumer
          .resource(
            ConsumerSettings[F, K, V]
              .withBootstrapServers(config.bootstrapServers.toString)
              .withGroupId(config.groupId)
              .withAutoOffsetReset(config.autoOffsetReset)
              .withEnableAutoCommit(config.autoCommitEnabled)
              .withDefaultApiTimeout(config.apiTimeout)
          )
          .evalTap(_.subscribe(config.topics))
      log <- Slf4jLogger.create.toResource
    } yield new Impl(consumer, log)

  private final class Impl[F[_] : Concurrent, K : Show, V : Show](
      consumer: KafkaConsumer[F, K, V],
      log: Logger[F]
  ) extends Consumer[F, K, V] {

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

    def partitionedStream: Stream[F, Stream[F, CommittableConsumerRecord[F, K, V]]] =
      Stream.eval(log.info("Partitioned stream started")) >>
        consumer.partitionedStream.map {
          _.evalTap(processRecord(_))
        }

    def partitionsMapStream: Stream[F, Map[TopicPartition, Stream[F, CommittableConsumerRecord[F, K, V]]]] =
      Stream.eval(log.info("Partitions map stream started")) >>
        consumer.partitionsMapStream.map { streamMap =>
          streamMap.map { case (partition, stream) =>
            partition -> stream.evalTap(processRecord(_, partition.some))
          }
        }

    def assignmentStream: Stream[F, SortedSet[TopicPartition]] =
      Stream.eval(log.info("Assignment stream started")) >>
        consumer.assignmentStream.evalTap { partitions =>
          log.info("Consumer partitions assignment changed by rebalance: " + partitions.mkString(", "))
        }

    def partitionsFor(topic: String): F[List[PartitionInfo]] =
      consumer.partitionsFor(topic).flatTap { partitions =>
        log.info(s"Partitions of topic $topic:\n" + partitions.mkString("\n"))
      }
  }
}
