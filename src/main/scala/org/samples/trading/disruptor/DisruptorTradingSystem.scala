package org.samples.trading.disruptor

import org.samples.trading.common.TradingSystem
import org.samples.trading.domain.Order
import org.samples.trading.domain.Orderbook

import com.lmax.disruptor.EventFactory

class DisruptorTradingSystem extends TradingSystem {
  type ME = DisruptorMatchingEngine
  type OR = DisruptorOrderReceiver

  override def createMatchingEngines: List[MatchingEngineInfo] = {
    for {
      (orderbooks, i) ← orderbooksGroupedByMatchingEngine.zipWithIndex
      n = i + 1
    } yield {
      val me = new DisruptorMatchingEngine("ME" + i, orderbooks)
      val orderbooksCopy = orderbooks map (o ⇒ Orderbook(o.symbol, true))
      val standbyOption =
        if (useStandByEngines) {
          val meStandby = new DisruptorMatchingEngine("ME" + i + "s", orderbooksCopy)
          Some(meStandby)
        } else {
          None
        }

      MatchingEngineInfo(me, standbyOption, orderbooks)
    }
  }

  override def createOrderReceivers: List[DisruptorOrderReceiver] = {
    (1 to 10).toList map (i ⇒ {
      val orderReceiver = new DisruptorOrderReceiver()
      orderReceiver.ordinal = i - 1
      orderReceiver.numberOfConsumers = 10
      orderReceiver
    })
  }

  override def start() {

    for (MatchingEngineInfo(p, s, o) ← matchingEngines) {
      p.standby = s
    }
    val routing = matchingEngineRouting
    for (or ← orderReceivers) {
      or.updateRouting(routing)
    }

    orderReceivers.foreach(orderReceiver => {
      orderReceiver.disruptor.handleEventsWith(orderReceiver)
      orderReceiver.disruptor.start
    })

  }

  override def shutdown() {
    for (MatchingEngineInfo(p, s, o) ← matchingEngines) {
      p.exit()
      // standby is optional
      s.foreach(_.exit())
    }
    orderReceivers.foreach(orderReceiver => {
      orderReceiver.disruptor.halt
    })
  }
}

class OrderEvent {
  var order: Order = null
}

object OrderEventFactory {
  val EVENT_FACTORY: EventFactory[OrderEvent] = new EventFactory[OrderEvent]() {
    def newInstance(): OrderEvent =
      {
        new OrderEvent;
      }
  };
}