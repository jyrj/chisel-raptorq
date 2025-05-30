package raptorfecgen

import chisel3._
import chisel3.util._

/** RS(255, 223) systematic encoder – one 8-bit symbol per cycle.            */
object RSEncoder {
  /* generator-polynomial coefficients g₀…g₃₁ (no leading 1) */
  val genCoeffs: Seq[Int] = Seq(
    0xe8, 0x1d, 0xbd, 0x32, 0x8e, 0xf6, 0xe8, 0x0f,
    0x2b, 0x52, 0xa4, 0xee, 0x01, 0x9e, 0x0d, 0x77,
    0x9e, 0xe0, 0x86, 0xe3, 0xd2, 0xa3, 0x32, 0x6b,
    0x28, 0x1b, 0x68, 0xfd, 0x18, 0xef, 0xd8, 0x2d
  )
}

class RSEncoder(p: RaptorFECParameters) extends Module {
  require(p.symbolBits == 8, "encoder currently supports 8-bit symbols only")
  import RSEncoder._

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(p.sourceK, UInt(8.W))))
    val out = Decoupled(Vec(p.totalSymbolsRS, UInt(8.W)))
  })

  private val genVec  = VecInit(genCoeffs.map(_.U(8.W)))
  private val multirs = Seq.fill(p.numParitySymbolsRS)(Module(new GF256Multiplier(p)))

  for (i <- multirs.indices) {      // tie constant ‘b’ legs once
    multirs(i).io.b := genVec(p.numParitySymbolsRS - 1 - i) 
  }

  val sIdle :: sEncode :: sDone :: Nil = Enum(3)
  val state      = RegInit(sIdle)
  val srcReg     = Reg(Vec(p.sourceK, UInt(8.W)))
  val parityReg  = RegInit(VecInit(Seq.fill(p.numParitySymbolsRS)(0.U(8.W))))
  val outBuf     = Reg(Vec(p.totalSymbolsRS, UInt(8.W)))
  val idx        = RegInit(0.U(log2Ceil(p.sourceK).W))

  val feedback   = WireDefault(0.U(8.W))
  multirs.foreach(_.io.a := feedback)

  io.in.ready  := (state === sIdle)
  io.out.bits  := outBuf
  io.out.valid := (state === sDone)

  /* ------------------------------ FSM ---------------------------------- */
  switch(state) {
    is(sIdle) {
      when(io.in.valid && io.in.ready) {
        srcReg := io.in.bits
        parityReg.foreach(_ := 0.U)
        idx   := 0.U
        state := sEncode
      }
    }

    is(sEncode) {
      feedback := srcReg(idx) ^ parityReg.last

      val nextParity = Wire(Vec(p.numParitySymbolsRS, UInt(8.W)))
      nextParity(0) := multirs.head.io.product
      for (i <- 1 until p.numParitySymbolsRS) {
        nextParity(i) := parityReg(i - 1) ^ multirs(i).io.product
      }
      parityReg := nextParity

      when(idx === (p.sourceK - 1).U) {
        for (i <- 0 until p.sourceK)              outBuf(i)               := srcReg(i)
        for (i <- 0 until p.numParitySymbolsRS)   // reverse parity order
          outBuf(p.sourceK + i) := nextParity(p.numParitySymbolsRS - 1 - i)
        state := sDone
      }.otherwise {
        idx := idx + 1.U
      }

      // printf(p"[RS-ENC] idx=${idx}  fb=${feedback}\n")
    }
    is(sDone) {
      when(io.out.ready) { state := sIdle }
    }
  }
}
