package consumer

import cats.data.{NonEmptyList => Nel}
import common.BootstrapServersConfig
import fs2.kafka.AutoOffsetReset

final case class Config(
    bootstrapServers: BootstrapServersConfig,
    topics: Nel[String],
    groupId: String,
    autoOffsetReset: AutoOffsetReset,
    autoCommitEnabled: Boolean
)
