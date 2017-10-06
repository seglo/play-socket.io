package chat

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ BroadcastHub, Flow, Keep, MergeHub }
import play.api.libs.json.Format
import play.engineio.EngineIOController
import play.api.libs.functional.syntax._
import play.socketio.scaladsl.SocketIO

/**
 * A simple chat engine.
 */
class ChatEngine(socketIO: SocketIO)(implicit mat: Materializer) {

  import play.socketio.scaladsl.SocketIOEventCodec._

  // This will decode String "chat message" events coming in
  val decoder = decodeByName {
    case "chat message" => decodeJson[String]
  }

  // This will encode String "chat message" events going out
  val encoder = encodeByType[String] {
    case _: String => "chat message" -> encodeJson[String]
  }

  case class ChatMessageWithAck(message: String, ack: String => Unit)

  // This will decode String "chat message" events coming in
  val decoderAck = decodeByName {
    case "chat message ack" => decodeJson[String] withAckEncoder encodeJson[String] andThen {
      case (message, ack) => ChatMessageWithAck(message, ack)
    }
  }

  // This will encode String "chat message" events going out
  val encoderAck = encodeByType[String] {
    case _: String => "chat message ack" -> (encodeJson[String] withAckDecoder decodeJson[String])
  }

  private val chatFlow: Flow[String, String, NotUsed] = {
    // We use a MergeHub to merge all the incoming chat messages from all the
    // connected users into one flow, and we feed that straight into a
    // BroadcastHub to broadcast them out again to all the connected users.
    // See http://doc.akka.io/docs/akka/snapshot/scala/stream/stream-dynamic.html
    // for details on these features.
    val (sink, source) = MergeHub.source[String]
      .toMat(BroadcastHub.sink)(Keep.both).run

    // We couple the sink and source together so that one completes, the other
    // will to, and we use this to handle our chat
    Flow.fromSinkAndSourceCoupled(sink, source)
  }

  private val chatFlowAck = {
    // We use a MergeHub to merge all the incoming chat messages from all the
    // connected users into one flow, and we feed that straight into a
    // BroadcastHub to broadcast them out again to all the connected users.
    // See http://doc.akka.io/docs/akka/snapshot/scala/stream/stream-dynamic.html
    // for details on these features.
    val (sink, source) = MergeHub.source[ChatMessageWithAck]
      .map {
        case ChatMessageWithAck(msg, ack) =>
          val ackMessage = s"$msg ack"
          ack(ackMessage)
          ackMessage
      }
      .toMat(BroadcastHub.sink)(Keep.both).run

    // We couple the sink and source together so that one completes, the other
    // will to, and we use this to handle our chat
    Flow.fromSinkAndSourceCoupled(sink, source)
  }

  // Here we create an EngineIOController to handle requests for our chat
  // system, and we add the chat flow under the "/chat" namespace.
  val controller: EngineIOController = socketIO.builder
    .addNamespace("/chat", decoder, encoder, chatFlow)
    .addNamespace("/chatack", decoderAck, encoderAck, chatFlowAck)
    .createController()
}