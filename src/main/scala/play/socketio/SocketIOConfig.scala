package play.socketio

import play.api.Configuration

import scala.concurrent.duration._
import javax.inject.{Inject, Provider, Singleton}

/**
  * Configuration for socket.io.
  */
case class SocketIOConfig(
  ackDeadline: FiniteDuration = 60.seconds,
  ackCleanupEvery: Int = 10
)

object SocketIOConfig {
  def fromConfiguration(configuration: Configuration) = {
    val config = configuration.get[Configuration]("play.socket-io")
    SocketIOConfig(
      ackDeadline = config.get[FiniteDuration]("ack-deadline"),
      ackCleanupEvery = config.get[Int]("ack-cleanup-every")
    )
  }
}

@Singleton
class SocketIOConfigProvider @Inject() (configuration: Configuration) extends Provider[SocketIOConfig] {
  override lazy val get: SocketIOConfig = SocketIOConfig.fromConfiguration(configuration)
}
