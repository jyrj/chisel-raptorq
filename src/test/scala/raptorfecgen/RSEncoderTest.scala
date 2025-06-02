package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.mutable.ArrayBuffer

class RSEncoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Streaming RSEncoder"

  val p = RaptorFECParameters()
  val testTimeoutCycles = (p.totalSymbolsRS + p.sourceK) * 5 + 500 

  it should "match the Scala reference model for a streaming block" in {
    test(new RSEncoder(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val srcData = Seq.fill(p.sourceK)(scala.util.Random.nextInt(256))
      val golden = ReferenceRS.encode(srcData)
      val receivedData = ArrayBuffer[Int]()

      dut.io.out.ready.poke(true.B) // Keep receiver ready throughout
      dut.io.in.valid.poke(false.B) // Default to not valid
      dut.clock.step(1) 

      var sentSourceCount = 0
      var receivedTotalCount = 0
      var cycles = 0

      println(s"RSEncoderTest: Starting test. Expecting K=${p.sourceK} source, P=${p.numParitySymbolsRS} parity.")

      // Combined driver and receiver logic in a single thread
      while ((sentSourceCount < p.sourceK || receivedTotalCount < p.totalSymbolsRS) && cycles < testTimeoutCycles) {
        
        // Driver Logic (send one source symbol if conditions met)
        if (sentSourceCount < p.sourceK && dut.io.in.ready.peek().litToBoolean) {
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.data.poke(srcData(sentSourceCount).U)
          dut.io.in.bits.last.poke(sentSourceCount == p.sourceK - 1)
          // println(s"RSEncoderTest: Driving source symbol ${sentSourceCount} (${srcData(sentSourceCount).toHexString}), last=${sentSourceCount == p.sourceK - 1}")
        } else if (sentSourceCount >= p.sourceK) {
          dut.io.in.valid.poke(false.B) // Stop sending after all source symbols
        }

        // Receiver Logic (consume one output symbol if conditions met)
        if (dut.io.out.valid.peek().litToBoolean && dut.io.out.ready.peek().litToBoolean) {
          receivedData += dut.io.out.bits.data.peek().litValue.toInt
          // println(s"RSEncoderTest: Received symbol ${receivedData.last.toHexString}, total ${receivedData.length}, DUT last: ${dut.io.out.bits.last.peek().litToBoolean}")
          receivedTotalCount += 1
        }
        
        dut.clock.step(1)
        cycles += 1

        // After stepping, if input was valid, it's now considered sent for next iteration
        if (dut.io.in.valid.peek().litToBoolean && dut.io.in.ready.peek().litToBoolean) {
            sentSourceCount +=1
             // If we just sent a symbol, de-assert valid for the next cycle unless we immediately send another
            if (sentSourceCount >= p.sourceK) {
                 dut.io.in.valid.poke(false.B)
            }
        }
      }
      
      println(s"RSEncoderTest: Main loop finished. Cycles=${cycles}, SentSource=${sentSourceCount}, ReceivedTotal=${receivedTotalCount}")
      assert(cycles < testTimeoutCycles, s"Test timed out. Sent ${sentSourceCount}/${p.sourceK}, Received ${receivedTotalCount}/${p.totalSymbolsRS}")

      assert(receivedData.length == p.totalSymbolsRS, s"Expected ${p.totalSymbolsRS} symbols, but got ${receivedData.length}")
      for (i <- 0 until p.totalSymbolsRS) {
        assert(receivedData(i) == golden(i), s"Symbol $i mismatch: Got ${receivedData(i).toHexString}, Expected ${golden(i).toHexString}")
      }
    }
  }
}