package common

final case class BootstrapServersConfig(host: String, port: Int) {
  override def toString: String = s"$host:$port"
}

object BootstrapServersConfig {
  val Default: BootstrapServersConfig = BootstrapServersConfig(host, port)
}
