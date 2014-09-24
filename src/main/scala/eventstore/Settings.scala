package eventstore

import scala.concurrent.duration._
import java.net.InetSocketAddress
import com.typesafe.config.{ ConfigFactory, Config }

/**
 * @param address IP & port of Event Store
 * @param connectionTimeout The desired connection timeout
 * @param maxReconnections Maximum number of reconnections before backing off
 * @param reconnectionDelayMin Delay before first reconnection
 * @param reconnectionDelayMax Maximum delay on reconnections
 * @param defaultCredentials The [[UserCredentials]] to use for operations where other [[UserCredentials]] are not explicitly supplied.
 * @param heartbeatInterval The interval at which to send heartbeat messages.
 * @param heartbeatTimeout The interval after which an unacknowledged heartbeat will cause the connection to be considered faulted and disconnect.
 * @param operationTimeout The amount of time before an operation is considered to have timed out
 * @param readBatchSize Number of events to be retrieved by client as single message
 * @param backpressure see [[eventstore.pipeline.BackpressureBuffer]]
 */
case class Settings(
  address: InetSocketAddress = new InetSocketAddress("127.0.0.1", 1113),
  connectionTimeout: FiniteDuration = 1.second,
  maxReconnections: Int = 100,
  reconnectionDelayMin: FiniteDuration = 250.millis,
  reconnectionDelayMax: FiniteDuration = 10.seconds,
  defaultCredentials: Option[UserCredentials] = Some(UserCredentials.defaultAdmin),
  heartbeatInterval: FiniteDuration = 500.millis,
  heartbeatTimeout: FiniteDuration = 2.seconds,
  operationTimeout: FiniteDuration = 5.seconds,
  readBatchSize: Int = 500,
  backpressure: BackpressureSettings = BackpressureSettings())

object Settings {
  private lazy val config: Config = ConfigFactory.load()
  lazy val Default: Settings = Settings(config)

  def apply(conf: Config): Settings = {
    def apply(conf: Config): Settings = {
      def duration(path: String) = FiniteDuration(conf.getDuration(path, MILLISECONDS), MILLISECONDS)
      Settings(
        address = new InetSocketAddress(conf getString "address.host", conf getInt "address.port"),
        maxReconnections = conf getInt "max-reconnections",
        reconnectionDelayMin = duration("reconnection-delay.min"),
        reconnectionDelayMax = duration("reconnection-delay.max"),
        defaultCredentials = for {
          l <- Option(conf getString "credentials.login")
          p <- Option(conf getString "credentials.password")
        } yield UserCredentials(login = l, password = p),
        heartbeatInterval = duration("heartbeat.interval"),
        heartbeatTimeout = duration("heartbeat.timeout"),
        connectionTimeout = duration("connection-timeout"),
        operationTimeout = duration("operation-timeout"),
        readBatchSize = conf getInt "read-batch-size",
        backpressure = BackpressureSettings(conf))
    }
    apply(conf.getConfig("eventstore"))
  }

  /**
   * Java API
   */
  def getInstance(): Settings = Default
}

/**
 * see [[eventstore.pipeline.BackpressureBuffer]]
 */
case class BackpressureSettings(lowWatermark: Int = 100, highWatermark: Int = 10000, maxCapacity: Int = 1000000) {
  require(lowWatermark >= 0, s"lowWatermark must be >= 0, but is $lowWatermark")
  require(highWatermark >= lowWatermark, s"highWatermark must be >= lowWatermark, but $highWatermark < $lowWatermark")
  require(maxCapacity >= highWatermark, s"maxCapacity >= highWatermark, but $maxCapacity < $highWatermark")
}

object BackpressureSettings {
  def apply(conf: Config): BackpressureSettings = BackpressureSettings(
    lowWatermark = conf getInt "backpressure.low-watermark",
    highWatermark = conf getInt "backpressure.high-watermark",
    maxCapacity = conf getInt "backpressure.max-capacity")
}