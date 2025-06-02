package raptorfecgen

import chisel3._
import chisel3.util._

class RSDecoder(val p: RaptorFECParameters) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new FECDataBundle(p))) // FIX: Standard DecoupledIO
    val out = Decoupled(new FECDataBundle(p))      // FIX: Standard DecoupledIO
    val error = Output(Bool())
  })

  val s_idle :: s_receiving :: s_calc_syndromes :: s_find_errors :: s_correct_errors :: s_outputting :: Nil = Enum(6)
  val state = RegInit(s_idle)

  val received_codeword = Reg(Vec(p.totalSymbolsRS, UInt(p.symbolBits.W)))
  val received_count = Reg(UInt(log2Ceil(p.totalSymbolsRS + 1).W))
  val corrected_symbols = Reg(Vec(p.sourceK, UInt(p.symbolBits.W)))
  val output_count = Reg(UInt(log2Ceil(p.sourceK + 1).W))
  val unrecoverable_error = RegInit(false.B)

  io.in.ready := (state === s_receiving)
  io.out.valid := (state === s_outputting)
  io.out.bits.data := corrected_symbols(output_count)
  io.out.bits.last := (output_count === (p.sourceK - 1).U)
  io.error := unrecoverable_error

  val syndrome_mults = Seq.fill(p.numParitySymbolsRS)(Module(new GF256Multiplier(p)))
  syndrome_mults.foreach { m => m.io.a := 0.U; m.io.b := 0.U }

  switch(state) {
    is(s_idle) {
      unrecoverable_error := false.B
      received_count := 0.U
      output_count := 0.U
      // Stay idle until input becomes valid and we transition in s_receiving
      when(io.in.valid) { // Check if input is trying to send
          state := s_receiving
      }
    }
    is(s_receiving) {
      io.in.ready := true.B // Always ready to receive in this state
      when(io.in.fire) {
        received_codeword(received_count) := io.in.bits.data
        received_count := received_count + 1.U
        when(io.in.bits.last || received_count === (p.totalSymbolsRS -1).U) {
          state := s_calc_syndromes
        }
      }
    }
    is(s_calc_syndromes) {
      state := s_find_errors
    }
    is(s_find_errors) {
      state := s_correct_errors
    }
    is(s_correct_errors) {
      for (i <- 0 until p.sourceK) {
        corrected_symbols(i) := received_codeword(i)
      }
      state := s_outputting
    }
    is(s_outputting) {
      io.out.valid := true.B // Data in corrected_symbols is ready
      when(io.out.fire) {
        output_count := output_count + 1.U
        when(io.out.bits.last) {
          state := s_idle
        }
      }
    }
  }
}