package org.samples.trading.disruptor

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import scala.concurrent.ops.spawn

import org.junit.Assert.assertTrue
import org.junit.Test
import org.samples.trading.common.BenchmarkScenarios
import org.samples.trading.domain.Order
import org.samples.trading.domain.Rsp
import org.samples.trading.domain.TotalTradeCounter

import com.lmax.disruptor.EventTranslator

class DisruptorPerformanceTest extends BenchmarkScenarios {
  type TS = DisruptorTradingSystem

  override def createTradingSystem: TS = new DisruptorTradingSystem

  override def placeOrder(orderReceiver: DisruptorOrderReceiver, order: Order): Rsp = {
    orderReceiver.asInstanceOf[DisruptorOrderReceiver].placeOrder(order)
  }

  // need this so that junit will detect this as a test case
  @Test
  def dummy {
  }

  override def runScenario(scenario: String, orders: List[Order], repeat: Int, numberOfClients: Int, delayMs: Int) = {
    val totalNumberOfRequests = orders.size * repeat
    val repeatsPerClient = repeat / numberOfClients
    val oddRepeats = repeat - (repeatsPerClient * numberOfClients)
    val latch = new CountDownLatch(numberOfClients)
    TotalTradeCounter.latchTrade = new CountDownLatch((orders.size / 2) * repeat)
    val receivers = tradingSystem.orderReceivers.toIndexedSeq
    val clients = (for (i <- 0 until numberOfClients) yield {
      val receiver = receivers(i % receivers.size)
      new Client(i, receivers.size, receiver, orders, latch, repeatsPerClient + (if (i < oddRepeats) 1 else 0), delayMs)
    }).toList

    val start = System.nanoTime
    clients.foreach(c => spawn(c.run))

    import scala.collection.JavaConverters._
    val ok = latch.await((5000 + (2 + delayMs) * totalNumberOfRequests) * timeDilation, TimeUnit.MILLISECONDS)
    println("ok=" + ok + ":" + repeat + ":" + (orders.size / 2) * repeat + ":" + TotalTradeCounter.counter.get)
    //println("ok hasBacklog:" + receivers.map(_.disruptor.hasBacklog()).mkString(":"))
    //println("ok backlogLength:" + receivers.map(_.disruptor.backlogLength().asScala.mkString(",")).mkString(":"))

    val ok1 = TotalTradeCounter.latchTrade.await((5000 + (2 + delayMs) * totalNumberOfRequests) * timeDilation, TimeUnit.MILLISECONDS)
    println("ok1=" + ok1 + ":" + repeat + ":" + (orders.size / 2) * repeat + ":" + TotalTradeCounter.counter.get)
    //println("ok1 hasBacklog:" + receivers.map(_.disruptor.hasBacklog()).mkString(":"))
    //println("ok1 backlogLength:" + receivers.map(_.disruptor.backlogLength().asScala.mkString(",")).mkString(":"))
    //    val ok = latch.await((5000 + (2 + delayMs) * totalNumberOfRequests) * timeDilation, TimeUnit.MILLISECONDS)
    val durationNs = (System.nanoTime - start)

    println(repeat + ":" + (orders.size / 2) * repeat + ":" + TotalTradeCounter.counter.get)
    assertTrue(ok)
    assertTrue(ok1)
    //    assertEquals((orders.size / 2) * repeat, TotalTradeCounter.counter.get)
    logMeasurement(scenario, numberOfClients, durationNs)
  }

  class Client(ordinal: Long, numberOfConsumers: Long, orderReceiver: DisruptorOrderReceiver, orders: List[Order], latch: CountDownLatch, repeat: Int, delayMs: Int)
      extends EventTranslator[OrderEvent] with Runnable {

    var currentOrder: Order = null

    override def translateTo(event: OrderEvent, sequence: Long): OrderEvent = {
      event.order = currentOrder
      event
    }

    override def run() {
      (1 to repeat).foreach(i =>
        {
          // println("Client repeat: " + i)
          for (o <- orders) {
            val t0 = System.nanoTime
            //            println("disruptor.publishEvent: " + o)
            //            val ringBuffer = disruptor.getRingBuffer()
            //
            //            var sequence = ringBuffer.next()
            ////            println(ordinal + ":" + sequence)
            //            val event = ringBuffer.get(sequence)
            //            event.order = o
            //            // make the event available to EventProcessors
            //            ringBuffer.publish(sequence)
            currentOrder = o
            orderReceiver.disruptor.publishEvent(this)
            val duration = System.nanoTime - t0
            stat.addValue(duration)
            //            }
            delay(delayMs)
          }
        })
      //println(ordinal + ":" + orderReceiver.disruptor.hasBacklog + ":" + repeat * orders.size + ":" + orderReceiver.disruptor.getRingBuffer.getCursor)
      import scala.collection.JavaConverters._
      //println("ordinal backlogLength:"+ordinal + ":" +  orderReceiver.disruptor.backlogLength().asScala.mkString(","))
      latch.countDown()
    }
  }
}