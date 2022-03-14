package producer

import cats.FlatMap
import cats.effect.{Async, Resource}
import cats.syntax.flatMap._
import fs2.kafka._

trait Producer[F[_], K, V] {
  def produce[P](records: ProducerRecords[P, K, V]): F[ProducerResult[P, K, V]]
}

object Producer {

  def makeResource[F[_] : Async, K, V](
      config: Config
  )(implicit keySerializer: Serializer[F, K], valueSerializer: Serializer[F, V]): Resource[F, Producer[F, K, V]] =
    for {
      producer <-
        KafkaProducer
          .resource(
            ProducerSettings[F, K, V]
              .withBootstrapServers(config.bootstrapServers.toString)
          )
    } yield new Impl(producer)

  private final class Impl[F[_] : FlatMap, K, V](producer: KafkaProducer[F, K, V]) extends Producer[F, K, V] {
    def produce[P](records: ProducerRecords[P, K, V]): F[ProducerResult[P, K, V]] =
      producer.produce(records).flatten
  }
}
