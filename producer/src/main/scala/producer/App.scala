package producer

import cats.effect.std.Console
import cats.effect.{Async, IO, IOApp, Temporal}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import common._
import fs2.kafka._

import scala.concurrent.duration._

object App extends IOApp.Simple {

  def run: IO[Unit] = program[IO]

  private def program[F[_] : Async](implicit console: Console[F]): F[Unit] = {

    // type P = String
    type P = Unit
    type K = String
    type V = String

    val config = Config(BootstrapServersConfig.Default)

    def loop(producer: Producer[F, K, V]): F[Unit] =
      (nextRecord >>= producer.produce).foreverM

    def nextRecord: F[ProducerRecords[P, K, V]] = {

      def readLine(message: String): F[String] = console.print(message) *> console.readLine

      for {
        _     <- Temporal[F].sleep(500.millis)
        key   <- readLine("Key: ")
        value <- readLine("Value: ")
        topic <- readLine("Topic: ").map(input => if (input.isEmpty) topicA else "topic-" + input.toUpperCase)
        // passthrough <- readLine("Passthrough: ") // TODO what's the purpose?
        partition <- readLine("Partition: ").map(_.toIntOption.getOrElse(0))
        _         <- console.print("\n")
      } yield ProducerRecords.one(ProducerRecord(topic, key, value).withPartition(partition) /*, passthrough*/ )
    }

    Producer.makeResource[F, K, V](config).use(loop)
  }
}
