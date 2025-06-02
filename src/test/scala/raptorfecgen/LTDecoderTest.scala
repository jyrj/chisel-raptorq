package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.mutable.ArrayBuffer

class LTDecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Enhanced LTDecoder"

  val p = RaptorFECParameters(sourceK = 4, ltRepairCap = 4)
  val testTimeoutCycles = p.sourceK * (p.sourceK + p.ltRepairCap) * 5 + 200 // Increased timeout

  it should "decode a simple set of degree-1 symbols streamed with info" in {
    test(new LTDecoder(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val sourceData = Seq(0x1A, 0x2B, 0x3C, 0x4D)
      val receivedSymbolsWithInfo = sourceData.zipWithIndex.map { case (data, i) =>
        val info = Seq.tabulate(p.sourceK)(j => if (i == j) true.B else false.B)
        (data, info)
      }
      val recoveredData = ArrayBuffer[Int]()
      dut.io.recoveredSymbolsOut.ready.poke(true.B)
      dut.clock.step(1)

      val driver = fork {
        var driverCycles = 0
        for (i <- 0 until p.sourceK) {
          dut.io.receivedPacketIn.valid.poke(true.B)
          dut.io.receivedPacketIn.bits.data.poke(receivedSymbolsWithInfo(i)._1.U)
          dut.io.receivedPacketIn.bits.symbolInfo.zip(receivedSymbolsWithInfo(i)._2).foreach { case (port, v) => port.poke(v) }
          dut.io.receivedPacketIn.bits.isLastOfBlock.poke(i == p.sourceK - 1)

          var readyWaitCycles = 0
          while(!dut.io.receivedPacketIn.ready.peek().litToBoolean && readyWaitCycles < testTimeoutCycles) {
            readyWaitCycles+=1; driverCycles+=1; dut.clock.step(1)
          }
          assert(readyWaitCycles < testTimeoutCycles, "Timeout waiting for LTDecoder receivedPacketIn.ready")
          dut.clock.step(1); driverCycles+=1
        }
        dut.io.receivedPacketIn.valid.poke(false.B)
        println(s"LTDecoderTest: Driver finished in ${driverCycles} cycles for its part.")
      }

      var receiverCycles = 0
      // Loop until DUT signals completion or error, or we time out
      while(!(dut.io.decodingComplete.peek().litToBoolean || dut.io.error.peek().litToBoolean) && receiverCycles < testTimeoutCycles) {
        if (dut.io.recoveredSymbolsOut.valid.peek().litToBoolean && dut.io.recoveredSymbolsOut.ready.peek().litToBoolean) {
          recoveredData += dut.io.recoveredSymbolsOut.bits.data.peek().litValue.toInt
        }
        receiverCycles += 1
        dut.clock.step(1)
      }
      
      // After the main loop, if decodingComplete is true, try to collect any remaining symbols from the output buffer
      // This can happen if decodingComplete is asserted while the last symbol is still valid on the output
      if(dut.io.decodingComplete.peek().litToBoolean){
          var finalCollectCycles = 0
          while(recoveredData.length < p.sourceK && finalCollectCycles < p.sourceK + 5){ // Try to collect for a few more cycles
              if (dut.io.recoveredSymbolsOut.valid.peek().litToBoolean && dut.io.recoveredSymbolsOut.ready.peek().litToBoolean) {
                 recoveredData += dut.io.recoveredSymbolsOut.bits.data.peek().litValue.toInt
              }
              finalCollectCycles +=1
              if(recoveredData.length == p.sourceK) { /* break */ } // Break not available, loop will terminate
              else { dut.clock.step(1) }
          }
      }

      driver.join()
      
      println(s"LTDecoderTest: Receiver loop finished after ${receiverCycles} cycles. Recovered ${recoveredData.length} symbols.")
      assert(receiverCycles < testTimeoutCycles, "Test timed out waiting for LTDecoder to complete/error.")
      
      dut.io.error.expect(false.B, "Decoder flagged an error unexpectedly.")
      dut.io.decodingComplete.expect(true.B, "Decoding did not complete successfully according to DUT.")
      
      assert(recoveredData.length == p.sourceK, s"Expected ${p.sourceK} recovered symbols, but got ${recoveredData.length}")
      assert(recoveredData.toSeq == sourceData, "Recovered data did not match source data.")
    }
  }
}

