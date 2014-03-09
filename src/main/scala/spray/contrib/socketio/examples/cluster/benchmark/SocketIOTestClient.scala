package spray.contrib.socketio.examples.benchmark.cluster

import akka.actor.ActorRef
import akka.io.IO
import scala.collection.mutable
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket
import spray.can.websocket.frame.TextFrame
import spray.contrib.socketio
import spray.contrib.socketio.examples.benchmark.cluster.SocketIOLoadTester.MessageArrived
import spray.contrib.socketio.examples.benchmark.cluster.SocketIOLoadTester.OnClose
import spray.contrib.socketio.examples.benchmark.cluster.SocketIOLoadTester.OnOpen
import spray.contrib.socketio.packet.EventPacket
import spray.contrib.socketio.packet.Packet
import spray.json.JsObject
import spray.json.JsString

object SocketIOTestClient {
  private var _nextId = 0
  private def nextId = {
    _nextId += 1
    _nextId
  }

  val wsHandshakeReq = websocket.basicHandshakeRepuset("/mytest")
  case object SendTimestampedChat
  case object SendHello
}

class SocketIOTestClient(connect: Http.Connect, report: ActorRef) extends socketio.SocketIOClientConnection {
  val Id = SocketIOTestClient.nextId.toString

  import context.system
  IO(UHttp) ! connect

  def businessLogic: Receive = {
    case SocketIOTestClient.SendHello           => connection ! TextFrame("5:::{\"name\":\"hello\", \"args\":[]}")
    case SocketIOTestClient.SendTimestampedChat => connection ! TextFrame(timestampedChat)
  }

  override def onDisconnected(endpoint: String) {
    report ! OnClose
  }

  override def onOpen() {
    report ! OnOpen
  }

  def onPacket(packet: Packet) {
    val messageArrivedAt = System.currentTimeMillis
    packet match {
      case x: EventPacket =>
        x.args.headOption match {
          case Some(JsObject(fields)) =>
            fields.get("text") match {
              case Some(JsString(message)) =>
                message.split(",") match {
                  case Array(Id, timestamp) =>
                    val roundtripTime = messageArrivedAt - timestamp.toLong
                    log.debug("roundtripTime {}", roundtripTime)
                    report ! MessageArrived(roundtripTime)
                  case _ =>
                }
              case _ =>
            }

          case _ =>
        }
      case _ =>
    }

  }

  def chat(message: String): String = {
    "5:::{\"name\":\"chat\", \"args\":[{\"text\":\"" + message + "\"}]}"

  }

  def timestampedChat = {
    val message = Id + "," + System.currentTimeMillis
    chat(message)
  }

}