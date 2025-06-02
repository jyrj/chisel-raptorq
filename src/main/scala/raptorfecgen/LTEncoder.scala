package raptorfecgen

import chisel3._
import chisel3.util._
import raptorfecgen.RFC6330Helpers._

class LTRepairSymbolWithInfo(val p: RaptorFECParameters) extends Bundle {
  val data = UInt(p.symbolBits.W)
  val symbolInfo = Vec(p.sourceK, Bool())
  val isLastRepair = Bool()
}

class LTEncoder(val p: RaptorFECParameters) extends Module {
  val io = IO(new Bundle {
    val sourceSymbolsIn = Flipped(Decoupled(new FECDataBundle(p)))
    val numRepairSymbolsToGen = Input(UInt(log2Ceil(p.ltRepairCap + 1).W))
    val repairSymbolOut = Decoupled(new LTRepairSymbolWithInfo(p))
    val busy = Output(Bool())
  })

  val s_idle :: s_loading_symbols :: s_gen_repair :: s_output_repair :: Nil = Enum(4)
  val state = RegInit(s_idle)

  val source_sym_reg = Reg(Vec(p.sourceK, UInt(p.symbolBits.W)))
  val source_load_count = Reg(UInt(log2Ceil(p.sourceK + 1).W))
  val repair_gen_count = RegInit(0.U(log2Ceil(p.ltRepairCap + 1).W))
  val current_repair_esi = Reg(UInt(log2Ceil(p.sourceK + p.ltRepairCap).W))
  
  val output_buffer_valid = RegInit(false.B)
  val output_buffer_data = Reg(UInt(p.symbolBits.W))
  val output_buffer_info = Reg(Vec(p.sourceK, Bool()))
  val output_buffer_is_last = RegInit(false.B)
  val current_tuple = Reg(new TupleBundle(p.sourceK)) // Registered tuple

  io.sourceSymbolsIn.ready := (state === s_loading_symbols)
  io.repairSymbolOut.valid := output_buffer_valid
  io.repairSymbolOut.bits.data := output_buffer_data
  io.repairSymbolOut.bits.symbolInfo := output_buffer_info
  io.repairSymbolOut.bits.isLastRepair := output_buffer_is_last
  io.busy := (state =/= s_idle)

  // Debugging prints for LTEncoder state
  // printf(p"LTEncoder: State=${state}, LoadCnt=${source_load_count}, RepairCnt=${repair_gen_count}, NumToGen=${io.numRepairSymbolsToGen}, OutValid=${io.repairSymbolOut.valid}, OutReady=${io.repairSymbolOut.ready}\n")
  when(state === s_output_repair || state === s_gen_repair) {
    // printf(p"LTEncoder: CurrentTuple (d=${current_tuple.d}, b=${current_tuple.b}), ESI=${current_repair_esi}\n")
  }


  when(io.repairSymbolOut.fire) {
    output_buffer_valid := false.B
    // printf(p"LTEncoder: Repair symbol taken by test. RepairCnt now ${repair_gen_count} (was ${repair_gen_count-1.U}), NumToGen ${io.numRepairSymbolsToGen}.\n")
    // repair_gen_count was incremented when the symbol was *prepared* in s_gen_repair.
    // So we compare it directly with numRepairSymbolsToGen.
    if (p.ltRepairCap > 0) { // Avoids warning for 0-width compare if ltRepairCap is 0
        when(repair_gen_count === io.numRepairSymbolsToGen) {
             state := s_idle
             // printf(p"LTEncoder: All repair symbols sent. -> IDLE\n")
        } .otherwise {
             state := s_gen_repair // Generate next symbol
             // printf(p"LTEncoder: More repair symbols needed. -> GEN_REPAIR\n")
        }
    } else { // if ltRepairCap is 0, numRepairSymbolsToGen must be 0
        state := s_idle
    }
  }

  switch(state) {
    is(s_idle) {
      source_load_count := 0.U
      when(io.numRepairSymbolsToGen > 0.U) {
        repair_gen_count := 0.U // Reset for a new batch of repair symbols
        state := s_loading_symbols
        // printf(p"LTEncoder: IDLE -> LOADING_SYMBOLS (NumToGen=${io.numRepairSymbolsToGen})\n")
      }
    }
    is(s_loading_symbols) {
      when(io.sourceSymbolsIn.fire) {
        source_sym_reg(source_load_count) := io.sourceSymbolsIn.bits.data
        source_load_count := source_load_count + 1.U
        when(io.sourceSymbolsIn.bits.last || source_load_count === (p.sourceK - 1).U) {
          current_repair_esi := p.sourceK.U // Initial ESI for repair symbols
          state := s_gen_repair
          // printf(p"LTEncoder: LOADING_SYMBOLS -> GEN_REPAIR (All source loaded)\n")
        }
      }
    }
    is(s_gen_repair) {
      // This state calculates the tuple for the *next* symbol to be output
      // and increments counters. The actual symbol is computed in s_output_repair.
      current_tuple := generateTuple(current_repair_esi, p.sourceK.U, p.sourceK)
      state := s_output_repair
      // printf(p"LTEncoder: GEN_REPAIR -> OUTPUT_REPAIR (Preparing ESI=${current_repair_esi})\n")
    }
    is(s_output_repair) {
      // Combinatorially compute the symbol based on current_tuple (which is a Reg)
      val d_val = current_tuple.d
      val b_seed_val = current_tuple.b

      // --- Combinatorial calculation for XOR sum (calculated_repair_data) ---
      val calculated_repair_data_result = Wire(UInt(p.symbolBits.W))
      // Create a sequence of operands. Apply Mux for selection.
      val operands_for_xor = Seq.tabulate(p.sourceK) { k_idx_sel =>
          val actual_source_idx = RFC6330Helpers.prng_rand(b_seed_val, k_idx_sel.U, p.sourceK.U)
          Mux(k_idx_sel.U < d_val, source_sym_reg(actual_source_idx), 0.U(p.symbolBits.W))
      }
      // Reduce the sequence. If d_val is 0, result is 0. If d_val is 1, result is first operand.
      calculated_repair_data_result := operands_for_xor.reduceOption(_ ^ _).getOrElse(0.U(p.symbolBits.W))
      
      // --- Combinatorial calculation for symbol info ---
      val final_symbol_info_result = Wire(Vec(p.sourceK, Bool()))
      final_symbol_info_result.foreach(_ := false.B) // Initialize to all false
      for(k_idx_select <- 0 until p.sourceK){ // Iterate 'd_val' times effectively
          when(k_idx_select.U < d_val){
              val actual_source_idx = RFC6330Helpers.prng_rand(b_seed_val, k_idx_select.U, p.sourceK.U)
              final_symbol_info_result(actual_source_idx) := true.B
          }
      }

      output_buffer_data := calculated_repair_data_result
      output_buffer_info := final_symbol_info_result
      output_buffer_is_last := (repair_gen_count + 1.U === io.numRepairSymbolsToGen) // current symbol being prepared IS the last one
      output_buffer_valid := true.B
      
      // Increment for the *next* symbol, after this one is sent.
      // The actual increment for the *current* symbol should be when moving from gen_repair.
      // Let's adjust: repair_gen_count tracks symbols *made available*.
      // It is incremented *after* this symbol is picked up by the testbench.
      // The state transition logic after io.repairSymbolOut.fire will handle this.
      // We also need to advance ESI for the next tuple generation.
      current_repair_esi := current_repair_esi + 1.U
      repair_gen_count := repair_gen_count + 1.U
      
      // printf(p"LTEncoder: OUTPUT_REPAIR. ESI=${current_repair_esi-1.U} (d=${d_val}) Data=${calculated_repair_data_result}, Info=${final_symbol_info_result.asUInt}, IsLast=${output_buffer_is_last}, ValidOut=${output_buffer_valid}\n")
    }
  }
}