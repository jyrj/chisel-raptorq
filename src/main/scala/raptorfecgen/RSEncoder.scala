package raptorfecgen

import chisel3._
import chisel3.util._

object RSEncoder { /* ... genCoeffs ... */ 
  val genCoeffs: Seq[Int] = Seq(0xe8,0x1d,0xbd,0x32,0x8e,0xf6,0xe8,0x0f,0x2b,0x52,0xa4,0xee,0x01,0x9e,0x0d,0x77,0x9e,0xe0,0x86,0xe3,0xd2,0xa3,0x32,0x6b,0x28,0x1b,0x68,0xfd,0x18,0xef,0xd8,0x2d)
}

class RSEncoder(val p: RaptorFECParameters) extends Module {
  require(p.symbolBits == 8)
  import RSEncoder._

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new FECDataBundle(p)))
    val out = Decoupled(new FECDataBundle(p))
  })

  private val genVec  = VecInit(genCoeffs.map(_.U(8.W)))
  private val multirs = Seq.fill(p.numParitySymbolsRS)(Module(new GF256Multiplier(p)))
  for (i <- multirs.indices) { multirs(i).io.b := genVec(p.numParitySymbolsRS - 1 - i) }

  val sIdle :: sPassThruSource :: sOutputParity :: Nil = Enum(3)
  val state = RegInit(sIdle)

  val parityReg  = RegInit(VecInit(Seq.fill(p.numParitySymbolsRS)(0.U(8.W))))
  val sourceCount = RegInit(0.U(log2Ceil(p.sourceK + 1).W)) // Counts symbols processed
  val parityCount = RegInit(0.U(log2Ceil(p.numParitySymbolsRS + 1).W)) // Counts parity symbols sent
  val feedback = WireDefault(0.U(8.W))
  multirs.foreach(_.io.a := feedback)

  io.in.ready := false.B; io.out.valid := false.B
  io.out.bits.data := 0.U; io.out.bits.last := false.B

  (p"RSEncoder: State=${state}, sourceCount=${sourceCount}, parityCount=${parityCount}, InV=${io.in.valid}, InR=${io.in.ready}, InL=${if(io.in.valid.litOption.getOrElse(0)==1 && io.in.ready.litOption.getOrElse(0)==1) io.in.bits.last else 2.U}, OutV=${io.out.valid}, OutR=${io.out.ready}, OutL=${if(io.out.valid.litOption.getOrElse(0)==1 && io.out.ready.litOption.getOrElse(0)==1) io.out.bits.last else 2.U}\n")

  switch(state) {
    is(sIdle) {
      io.in.ready := true.B
      when(io.in.fire) {
        parityReg.foreach(_ := 0.U) // Reset parity for new block
        sourceCount := 0.U          // Reset source count
        parityCount := 0.U          // Reset parity count
        
        // Process first symbol's parity
        feedback := io.in.bits.data ^ 0.U // Parity reg is all zeros initially
        val nextParity = Wire(Vec(p.numParitySymbolsRS, UInt(8.W)))
        nextParity(0) := multirs.head.io.product
        for (i <- 1 until p.numParitySymbolsRS) { nextParity(i) := 0.U ^ multirs(i).io.product } // 0.U is parityReg(i-1) for first symbol
        parityReg := nextParity
        sourceCount := 1.U // First symbol now processed

        // Pass through first symbol
        io.out.valid := true.B 
        io.out.bits.data := io.in.bits.data
        io.out.bits.last := false.B // Not last of total K+P block

        when(1.U === p.sourceK.U) { // If K=1, this was the only source symbol
             // If output also fires (io.out.ready is true), then transition
            when(io.out.ready){ // Only transition if output is taken
                state := sOutputParity
                // // printf(p"RSEncoder: IDLE -> OUTPUT_PARITY (K=1 source symbol processed and sent)\n")
            } .otherwise {
                // Stay here, output still valid, wait for ready
                state := sIdle // Effectively re-evaluates with output valid
                sourceCount := 0.U // Re-process this one symbol when out.ready comes
                parityReg.foreach(_ := 0.U) // And reset parity for re-calc
                // // printf(p"RSEncoder: IDLE. K=1 but output not ready. Re-evaluating first symbol.\n")
            }
        } .otherwise { // K > 1
            when(io.out.ready){ // Only transition if output is taken
                state := sPassThruSource
                // // printf(p"RSEncoder: IDLE -> PASSTHRU (first source symbol sent, K>1)\n")
            } .otherwise {
                // Stay here, output still valid, wait for ready
                state := sIdle 
                sourceCount := 0.U 
                parityReg.foreach(_ := 0.U)
                // // printf(p"RSEncoder: IDLE. First symbol output not ready. Re-evaluating.\n")
            }
        }
      } .otherwise { // Not io.in.fire
          // Remain in idle, keep io.in.ready true
          parityCount := 0.U // Ensure these are reset if we stay idle
          sourceCount := 0.U
      }
    }
    is(sPassThruSource) {
      io.in.ready := io.out.ready // Can accept new input if output is ready
      io.out.valid := io.in.valid // Output is valid if input is valid
      io.out.bits.data := io.in.bits.data
      io.out.bits.last := false.B

      when(io.in.fire && io.out.fire) { // Both input consumed and output taken
        feedback := io.in.bits.data ^ parityReg.last
        val nextParity = Wire(Vec(p.numParitySymbolsRS, UInt(8.W)))
        nextParity(0) := multirs.head.io.product
        for (i <- 1 until p.numParitySymbolsRS) { nextParity(i) := parityReg(i - 1) ^ multirs(i).io.product }
        parityReg := nextParity
        
        val updatedSourceCount = sourceCount + 1.U
        sourceCount := updatedSourceCount
        // // printf(p"RSEncoder: PASSTHRU. Source ${sourceCount} consumed. UpdatedSourceCount=${updatedSourceCount}. InLast=${io.in.bits.last}\n")

        when(updatedSourceCount === p.sourceK.U) { 
            state := sOutputParity
            // // printf(p"RSEncoder: PASSTHRU -> OUTPUT_PARITY (All ${p.sourceK} source symbols processed)\n")
        }
      }
    }
    is(sOutputParity) {
      io.in.ready := false.B 
      io.out.valid := true.B
      io.out.bits.data := parityReg(p.numParitySymbolsRS.U - 1.U - parityCount)
      val isLastParitySymbol = parityCount === (p.numParitySymbolsRS - 1).U
      io.out.bits.last := isLastParitySymbol

      when(io.out.fire) {
        val updatedParityCount = parityCount + 1.U
        parityCount := updatedParityCount
        // printf(p"RSEncoder: OUTPUT_PARITY. Parity ${parityCount} sent. UpdatedParityCount=${updatedParityCount}. IsLastOutput=${isLastParitySymbol}\n")
        when(updatedParityCount === p.numParitySymbolsRS.U) { 
            state := sIdle
            // printf(p"RSEncoder: OUTPUT_PARITY -> IDLE (All ${p.numParitySymbolsRS} parity symbols sent)\n")
        }
      }
    }
  }
}