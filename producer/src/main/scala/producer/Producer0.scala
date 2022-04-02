package producer

import cats.effect.Async
import cats.effect.kernel.{Concurrent, Resource}
import cats.syntax.functor._
import fs2.Stream
import fs2.kafka.{KafkaProducer, ProducerRecords, ProducerSettings, Serializer}

trait Producer0[F[_], K, V] {
  def stream[P](nextRecord: F[ProducerRecords[P, K, V]]): Stream[F, Unit]
}

object Producer0 {

  def makeResource[F[_] : Async, K, V](
      config: Config
  )(implicit KS: Serializer[F, K], VS: Serializer[F, V]): Resource[F, Producer0[F, K, V]] =
    for {
      settings <-
        Resource.pure {
          ProducerSettings[F, K, V].withBootstrapServers(config.bootstrapServers.toString)
        }
      producer <- KafkaProducer.resource(settings)
    } yield new Impl(settings, producer)

  private final class Impl[F[_] : Concurrent, K, V](
      settings: ProducerSettings[F, K, V],
      producer: KafkaProducer[F, K, V]
  ) extends Producer0[F, K, V] {
    def stream[P](nextRecord: F[ProducerRecords[P, K, V]]): Stream[F, Unit] =
      Stream
        .eval(nextRecord)
        .through(KafkaProducer.pipe[F, K, V, P](settings, producer))
        .repeat
        .void
  }
}
