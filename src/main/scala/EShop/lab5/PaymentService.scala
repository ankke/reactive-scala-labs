package EShop.lab5

import EShop.lab5.PaymentService.{PaymentClientError, PaymentServerError, PaymentSucceeded}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.pattern.pipe

object PaymentService {

  case object PaymentSucceeded // http status 200
  class PaymentClientError extends Exception // http statuses 400, 404
  class PaymentServerError extends Exception // http statuses 500, 408, 418

  def props(method: String, payment: ActorRef): Props = Props(new PaymentService(method, payment))

}

class PaymentService(method: String, payment: ActorRef) extends Actor with ActorLogging {

  implicit val executionContext = context.dispatcher

  private val http = Http(context.system)
  private val URI  = getURI

  override def preStart(): Unit = http.singleRequest(HttpRequest(uri = URI)).pipeTo(self)

  override def receive: Receive = LoggingReceive {
    case HttpResponse(code, _, _, _) =>
      code.intValue() match {
        case 200 =>
          payment ! PaymentSucceeded
          context.stop(self)
        case 400 | 404       => throw new PaymentClientError
        case 408 | 418 | 500 => throw new PaymentServerError
      }
  }

  private def getURI: String = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }

}
