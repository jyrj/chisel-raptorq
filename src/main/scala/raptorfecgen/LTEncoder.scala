package raptorfecgen

import chisel3._
import chisel3.util._

/** A simple Pseudo-Random Number Generator (PRNG) using a 16-bit LFSR.
  * This is used by the LT encoder to select source symbols.
  */
class PRNG(seed: Int) extends Module {
  val io = IO(new Bundle {
    val en = Input(Bool())
    val out = Output(UInt(16.W))
  })

  val lfsr = RegInit(seed.U(16.W))
  when(io.en) {
    // Taps for a 16-bit LFSR (Galois configuration)
    val bit = lfsr(0) ^ lfsr(2) ^ lfsr(3) ^ lfsr(5)
    lfsr := (lfsr >> 1) | (bit << 15)
  }
  io.out := lfsr
}


/** LT Encoder Implementation
  *
  * This module generates repair symbols based on the LT coding algorithm.
  * It uses a PRNG to select a set of source symbols, then XORs them together
  * to produce a single repair symbol.
  */
class LTEncoder(p: RaptorFECParameters) extends Module {
  val io = IO(new Bundle {
    // Input K source symbols
    val sourceSymbols = Flipped(Decoupled(Vec(p.sourceK, UInt(p.symbolBits.W))))
    // Control: number of repair symbols to generate for this block
    val numRepairSymbolsToGen = Input(UInt(log2Ceil(p.ltRepairCap + 1).W))
    // Output: one repair symbol at a time
    val repairSymbolOut = Decoupled(UInt(p.symbolBits.W))
  })

  // === States ===
  val s_idle :: s_loading_symbols :: s_gen_select_degree :: s_gen_xor_symbols :: s_gen_output :: Nil = Enum(5)
  val state = RegInit(s_idle)

  // === Registers ===
  val source_sym_reg = Reg(Vec(p.sourceK, UInt(p.symbolBits.W)))
  val repair_count = RegInit(0.U(log2Ceil(p.ltRepairCap + 1).W))
  val current_repair_symbol = Reg(UInt(p.symbolBits.W))
  val output_valid_reg = RegInit(false.B)
  val prng_seed = RegInit(0.U(16.W))

  // Internal PRNG for symbol selection
  val prng = Module(new PRNG(seed = 0xACE1))
  prng.io.en := (state === s_gen_xor_symbols)

  // Degree and selection counter
  val degree = Reg(UInt(log2Ceil(p.sourceK).W))
  val sel_idx = Reg(UInt(log2Ceil(p.sourceK).W))
  val xor_counter = Reg(UInt(log2Ceil(p.sourceK).W))

  // Default IO values
  io.sourceSymbols.ready := (state === s_idle)
  io.repairSymbolOut.valid := output_valid_reg
  io.repairSymbolOut.bits := current_repair_symbol

  // Handle output handshake
  when(output_valid_reg && io.repairSymbolOut.ready) {
    output_valid_reg := false.B
    when(repair_count < io.numRepairSymbolsToGen) {
      state := s_gen_select_degree
    }.otherwise {
      state := s_idle
    }
  }

  // State Machine Logic
  switch(state) {
    is(s_idle) {
      when(io.sourceSymbols.valid && io.numRepairSymbolsToGen > 0.U) {
        state := s_loading_symbols
      }
    }
    is(s_loading_symbols) {
      source_sym_reg := io.sourceSymbols.bits
      repair_count := 0.U
      prng_seed := 1.U // Reset seed for each new block
      state := s_gen_select_degree
    }
    is(s_gen_select_degree) {
      degree := (prng.io.out(4, 0) % (p.sourceK / 4).U) + 1.U // Degree between 1 and K/4
      xor_counter := 0.U
      current_repair_symbol := 0.U
      state := s_gen_xor_symbols
    }
    is(s_gen_xor_symbols) {
      // Select one symbol (based on PRNG output) and XOR it into the accumulator.
      sel_idx := prng.io.out % p.sourceK.U
      current_repair_symbol := current_repair_symbol ^ source_sym_reg(sel_idx)
      xor_counter := xor_counter + 1.U
      
      when(xor_counter >= (degree - 1.U)) {
        state := s_gen_output
      }
    }
    is(s_gen_output) {
      output_valid_reg := true.B
      repair_count := repair_count + 1.U
      // The state transition is handled by the output handshake logic
      state := s_idle // Will be overridden if more symbols needed
    }
  }
}