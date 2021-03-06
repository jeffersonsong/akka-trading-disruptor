package org.samples.trading.akka

import org.samples.trading.common.MatchingEngineRouting
import org.samples.trading.common.OrderReceiver
import org.samples.trading.domain.Order
import org.samples.trading.domain.Rsp

import akka.actor._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.dispatch.MessageDispatcher
import akka.event.EventHandler

class AkkaOrderReceiver(disp: Option[MessageDispatcher])
  extends Actor with OrderReceiver {
  type ME = ActorRef

  var orderCount = 0L
  // possibility to yield, due to starvation in some jvm/os
  val yieldCount = System.getProperty("benchmark.yieldCount", "0").toInt;

  for (d ← disp) {
    self.dispatcher = d
  }

  def receive = {
    case routing@MatchingEngineRouting(mapping) ⇒
      refreshMatchingEnginePartitions(routing.asInstanceOf[MatchingEngineRouting[ActorRef]])
    case order: Order ⇒
      placeOrder(order)
      if (yieldCount > 0 && orderCount % yieldCount == 0) {
        Thread.`yield`()
      }
      orderCount += 1
    case unknown ⇒ EventHandler.warning(this, "Received unknown message: " + unknown)
  }

  def placeOrder(order: Order) = {
    val matchingEngine = matchingEngineForOrderbook.get(order.orderbookSymbol)
    matchingEngine match {
      case Some(m) ⇒
        m.forward(order)
      case None ⇒
        EventHandler.warning(this, "Unknown orderbook: " + order.orderbookSymbol)
        self.channel ! new Rsp(false)
    }
  }
}