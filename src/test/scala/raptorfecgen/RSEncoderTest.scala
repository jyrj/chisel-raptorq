package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class RSEncoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RSEncoder"

  // Using default params: sourceK=223, totalSymbolsRS=255, symbolBits=8
  val params = RaptorFECParameters()

  it should "instantiate and pass basic data through (placeholder test)" in {
    test(new RSEncoder(params)) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.out.ready.poke(true.B)
      dut.clock.step(5) // Wait a bit

      // Prepare input data (K symbols)
      val sourceData = Seq.tabulate(params.sourceK)(i => (i % 256).U(params.symbolBits.W))
      
      dut.io.in.valid.poke(true.B)
      dut.io.in.bits.zip(sourceData).foreach { case (port, data) => port.poke(data) }
      
      // Wait for ready to go low
      while(dut.io.in.ready.peek().litToBoolean){
          dut.clock.step(1)
      }
      dut.io.in.valid.poke(false.B) // Deassert valid after accepted

      // Wait for output to be valid
      while(!dut.io.out.valid.peek().litToBoolean) {
        dut.clock.step(1)
      }

      // Check output (N symbols)
      // For now, just checks if the source part is copied correctly by the placeholder
      for (i <- 0 until params.sourceK) {
        dut.io.out.bits(i).expect(sourceData(i), s"Source symbol $i did not match")
      }
      // Parity symbols will be whatever the placeholder generates
      println(s"Placeholder RS Encoder output parity (first): ${dut.io.out.bits(params.sourceK).peek().litValue}")

      // Consume the output
      dut.io.out.ready.poke(true.B)
      dut.clock.step(1)
      // The previous poke to dut.io.out.valid was illegal and has been removed.
      
      // After consumption, the DUT should be ready for new input.
      dut.io.out.ready.poke(false.B) // Stop consuming.
      dut.io.in.ready.expect(true.B)
    }
  }
}