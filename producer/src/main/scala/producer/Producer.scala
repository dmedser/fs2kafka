package producer

import cats.effect.{Async, Concurrent, Resource}
import cats.syntax.flatMap._
import fs2.Stream
import fs2.kafka._

trait Producer[F[_], K, V] {
  def produce[P](records: ProducerRecords[P, K, V]): F[ProducerResult[P, K, V]]
  def stream[P](nextRecord: F[ProducerRecords[P, K, V]]): Stream[F, ProducerResult[P, K, V]]
}

object Producer {

  def makeResource[F[_] : Async, K, V](
      config: Config
  )(implicit KS: Serializer[F, K], VS: Serializer[F, V]): Resource[F, Producer[F, K, V]] =
    for {
      settings <-
        Resource.pure {
          ProducerSettings[F, K, V].withBootstrapServers(config.bootstrapServers.toString)
        }
      producer <- KafkaProducer.resource(settings)
    } yield new Impl(producer, settings)

  private final class Impl[F[_] : Concurrent, K, V](
      producer: KafkaProducer[F, K, V],
      settings: ProducerSettings[F, K, V]
  ) extends Producer[F, K, V] {
    def produce[P](records: ProducerRecords[P, K, V]): F[ProducerResult[P, K, V]] =
      producer.produce(records).flatten

    def stream[P](nextRecord: F[ProducerRecords[P, K, V]]): Stream[F, ProducerResult[P, K, V]] =
      Stream
        .eval(nextRecord)
        .through(KafkaProducer.pipe[F, K, V, P](settings, producer))
        .repeat
  }
}
