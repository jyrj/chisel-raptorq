package raptorfecgen

import chisel3._
import chisel3.util._

class RSDecoder(p: RaptorFECParameters) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(Vec(p.totalSymbolsRS, UInt(p.symbolBits.W))))
    val erasures = Input(Vec(p.totalSymbolsRS, Bool()))
    val out = Decoupled(Vec(p.sourceK, UInt(p.symbolBits.W)))
    val error = Output(Bool())
  })

  // === Internal States ===
  val s_idle :: s_calc_syndromes :: s_find_errors :: s_correct_errors :: s_done :: Nil = Enum(5)
  val state = RegInit(s_idle)

  // === Registers ===
  val received_codeword = Reg(Vec(p.totalSymbolsRS, UInt(p.symbolBits.W)))
  val syndromes = Reg(Vec(p.numParitySymbolsRS, UInt(p.symbolBits.W)))
  val error_locations = Reg(Vec(p.numParitySymbolsRS, Bool()))
  val corrected_symbols = Reg(Vec(p.sourceK, UInt(p.symbolBits.W)))
  val processing_complete = RegInit(false.B)
  val unrecoverable_error = RegInit(false.B)

  // Default IO values
  io.in.ready := (state === s_idle)
  io.out.valid := (state === s_done) && processing_complete
  io.out.bits := corrected_symbols
  io.error := unrecoverable_error

  // === Sub-Modules for GF(2^8) arithmetic ===
  val syndrome_mults = Seq.fill(p.numParitySymbolsRS)(Module(new GF256Multiplier(p)))

  syndrome_mults.foreach { m =>
    m.io.a := 0.U
    m.io.b := 0.U
  }

  val syndrome_accums = Reg(Vec(p.numParitySymbolsRS, UInt(p.symbolBits.W)))

  // State Machine Logic
  switch(state) {
    is(s_idle) {
      when(io.in.valid) {
        received_codeword := io.in.bits
        syndrome_accums.foreach(_ := 0.U)
        state := s_calc_syndromes
        unrecoverable_error := false.B
        processing_complete := false.B
      }
    }
    is(s_calc_syndromes) {
      // Placeholder logic
      val syndromes_non_zero = received_codeword.asUInt.orR
      syndromes := VecInit(Seq.fill(p.numParitySymbolsRS)(Mux(syndromes_non_zero, 0xAA.U, 0.U)))
      state := s_find_errors
    }
    is(s_find_errors) {
      // Placeholder logic
      val any_syndromes = syndromes.asUInt.orR
      unrecoverable_error := false.B
      error_locations.foreach(_ := false.B)
      state := s_correct_errors
    }
    is(s_correct_errors) {
      // Placeholder logic: just copy the received symbols
      for (i <- 0 until p.sourceK) {
        corrected_symbols(i) := received_codeword(i)
      }
      processing_complete := true.B
      state := s_done
    }
    is(s_done) {
      when(io.out.ready) {
        state := s_idle
        processing_complete := false.B
      }
    }
  }
}