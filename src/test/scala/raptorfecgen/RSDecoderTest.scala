package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.mutable.ArrayBuffer

class RSDecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Streaming RSDecoder"

  val p = RaptorFECParameters()
  val testTimeoutCycles = (p.totalSymbolsRS + p.sourceK) * 4 + 300

  it should "recover an error-free codeword from a stream" in {
    test(new RSDecoder(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val srcData = Seq.fill(p.sourceK)(scala.util.Random.nextInt(256))
      val encodedData = ReferenceRS.encode(srcData)
      val recoveredData = ArrayBuffer[Int]()

      dut.io.out.ready.poke(true.B)
      dut.clock.step(1)

      val driverThread = fork {
        var cyclesInDriver = 0
        for(i <- 0 until p.totalSymbolsRS) {
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.data.poke(encodedData(i).U)
          dut.io.in.bits.last.poke(i == p.totalSymbolsRS - 1)
          
          var readyWaitCycles = 0
          while(!dut.io.in.ready.peek().litToBoolean && readyWaitCycles < testTimeoutCycles) {
            readyWaitCycles +=1
            dut.clock.step(1)
          }
          assert(readyWaitCycles < testTimeoutCycles, s"Timeout waiting for RSDecoder input ready (driver thread, symbol $i)")
          dut.clock.step(1)
          cyclesInDriver +=1
        }
        dut.io.in.valid.poke(false.B)
        println(s"RSDecoderTest: Driver fork completed in ${cyclesInDriver} cycles for its part.")
      }
      
      driverThread.join()
      println(s"RSDecoderTest: Driver fork joined. Now receiving decoded symbols.")

      var receiverCycles = 0
      while(recoveredData.length < p.sourceK && receiverCycles < testTimeoutCycles) {
        if (dut.io.out.valid.peek().litToBoolean && dut.io.out.ready.peek().litToBoolean) {
          recoveredData += dut.io.out.bits.data.peek().litValue.toInt
        }
        receiverCycles += 1
        dut.clock.step(1)
      }
      assert(receiverCycles < testTimeoutCycles, s"Timeout waiting for RSDecoder output (receiver thread). Received ${recoveredData.length}/${p.sourceK}")
      
      dut.io.error.expect(false.B)
      assert(recoveredData.length == p.sourceK, s"Expected ${p.sourceK} symbols, but got ${recoveredData.length}")
      assert(recoveredData.toSeq == srcData, "Recovered data did not match source data")
    }
  }
}