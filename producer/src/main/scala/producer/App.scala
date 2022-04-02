package producer

import cats.effect.{IO, IOApp}
import cats.syntax.flatMap._
import common._
import fs2.kafka._

import scala.concurrent.duration._

object App extends IOApp.Simple {

  type K = String
  type V = String
  type P = Unit

  private val config: Config = Config(BootstrapServersConfig.Default)

  private def nextRecord: IO[ProducerRecords[P, K, V]] = {

    def readLine(message: String): IO[String] = IO.print(message) *> IO.readLine

    for {
      _     <- IO.sleep(500.millis)
      key   <- readLine("Key: ")
      value <- readLine("Value: ")
      topic <- readLine("Topic: ").map(input => if (input.isEmpty) topicA else "topic-" + input.toUpperCase)
      // passthrough <- readLine("Passthrough: ") // TODO what's the purpose?
      partition_? <- readLine("Partition: ").map(_.toIntOption)
      _           <- IO.print("\n")
    } yield ProducerRecords.one(
      record = {
        val record = ProducerRecord(topic, key, value)
        partition_?.fold(ifEmpty = record)(record.withPartition)
      }
      // passthrough = passthrough
    )
  }

  def program: IO[Unit] = {

    def loop(producer: Producer[IO, K, V]): IO[Unit] =
      (nextRecord >>= producer.produce).foreverM

    Producer.makeResource[IO, K, V](config).use(loop)
  }

  def program0: IO[Unit] =
    Producer0.makeResource[IO, K, V](config).use {
      _.stream(nextRecord).compile.drain
    }

  def run: IO[Unit] = program0
}
