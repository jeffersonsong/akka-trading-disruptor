package org.samples.trading.domain

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch

object TotalTradeCounter {
  val counter = new AtomicInteger
  
  var latchTrade : CountDownLatch = new CountDownLatch(0)

  def reset() {
    counter.set(0)
  }
}
