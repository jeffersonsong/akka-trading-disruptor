package org.samples.trading.disruptor

import java.util.concurrent.Executors

import org.samples.trading.common.MatchingEngineRouting
import org.samples.trading.common.OrderReceiver
import org.samples.trading.domain.Order
import org.samples.trading.domain.Rsp

import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.BlockingWaitStrategy
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.SingleThreadedClaimStrategy
import com.lmax.disruptor.MultiThreadedClaimStrategy
import com.lmax.disruptor.experimental.MultiThreadedClaimStrategyV2

class DisruptorOrderReceiver() extends OrderReceiver with EventHandler[OrderEvent] {
  type ME = DisruptorMatchingEngine

  var ordinal: Long = _
  var numberOfConsumers: Long = _

  val disruptor: Disruptor[OrderEvent] =
    new Disruptor[OrderEvent](OrderEventFactory.EVENT_FACTORY, Executors.newCachedThreadPool(),
      new SingleThreadedClaimStrategy(1024 * 1),
      //      new SingleThreadedClaimStrategy(1024 * 8),
      //      new YieldingWaitStrategy());
      new BlockingWaitStrategy());

  override def onEvent(event: OrderEvent, sequence: Long, endOfBatch: Boolean): Unit = {
    //    println("event.orderbookSymbol: " + event.order.orderbookSymbol)
    //    if ((sequence % numberOfConsumers) == ordinal) {
    val rsp = placeOrder(event.order)
    //    if (ordinal == 0) {
    //      println("sequence: " + sequence + "; ordinal: " + ordinal + ":" + rsp.status)
    //      if (!rsp.status) {
    //        println("Invalid rsp")
    //      }
    //
    //    }
    //    }
  }

  def placeOrder(order: Order): Rsp = {
    matchingEngineForOrderbook.get(order.orderbookSymbol) match {
      case Some(matchingEngine) ⇒
        matchingEngine.matchOrder(order)
      case None ⇒
        throw new IllegalArgumentException("Unknown orderbook: " + order.orderbookSymbol)
    }
  }

  def updateRouting(routing: MatchingEngineRouting[DisruptorMatchingEngine]) {
    refreshMatchingEnginePartitions(routing)
  }
}