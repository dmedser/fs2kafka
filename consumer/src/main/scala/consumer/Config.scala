package consumer

import cats.data.{NonEmptyList => Nel}
import common.BootstrapServersConfig
import fs2.kafka.AutoOffsetReset

import scala.concurrent.duration.FiniteDuration

final case class Config(
    bootstrapServers: BootstrapServersConfig,
    topics: Nel[String],
    groupId: String,
    autoOffsetReset: AutoOffsetReset,
    autoCommitEnabled: Boolean,
    apiTimeout: FiniteDuration
)
