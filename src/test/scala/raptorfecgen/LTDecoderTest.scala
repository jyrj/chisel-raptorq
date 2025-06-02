package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class LTDecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "LTDecoder"

  val p = RaptorFECParameters(sourceK = 4, ltRepairCap = 4)

  it should "decode a simple set of degree-1 symbols" in {
    test(new LTDecoder(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val sourceData = Seq(0x11, 0x22, 0x33, 0x44)

      // Prepare the degree-1 symbols (R_i = S_i)
      val receivedSymbols = sourceData
      val symbolInfos = Seq.tabulate(p.sourceK) { i =>
        Seq.tabulate(p.sourceK)(j => if (i == j) true.B else false.B)
      }

      dut.io.recoveredSymbols.ready.poke(true.B)
      dut.clock.step(1)

      // Feed the symbols one by one
      for (i <- 0 until p.sourceK) {
        dut.io.receivedSymbol.valid.poke(true.B)
        dut.io.receivedSymbol.bits.poke(receivedSymbols(i).U)
        dut.io.symbolInfo.zip(symbolInfos(i)).foreach { case (port, v) => port.poke(v) }
        
        while(!dut.io.receivedSymbol.ready.peek().litToBoolean) {
            dut.clock.step(1)
        }
        dut.clock.step(1)
      }
      dut.io.receivedSymbol.valid.poke(false.B)

      // Wait for decoding to complete
      var timeout = 0
      while(!dut.io.decodingComplete.peek().litToBoolean && timeout < 50) {
        dut.clock.step(1)
        timeout += 1
      }

      dut.io.decodingComplete.expect(true.B, "Decoding did not complete successfully")
      dut.io.error.expect(false.B, "Decoder flagged an error")
      dut.io.recoveredSymbols.valid.expect(true.B)
      dut.io.recoveredSymbols.bits.zip(sourceData).foreach { case(port, data) =>
        port.expect(data.U)
      }
    }
  }
}