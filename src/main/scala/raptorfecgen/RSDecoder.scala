package raptorfecgen

import chisel3._
import chisel3.util._

class RSDecoder(val p: RaptorFECParameters) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new FECDataBundle(p)))
    val out = Decoupled(new FECDataBundle(p))
    val error = Output(Bool())
    val decoding_done = Output(Bool())
    val debug_error_count = if(p.sourceK == 223) Some(Output(UInt(5.W))) else None
  })

  val s_idle :: s_receiving_and_calc :: s_check_syndromes :: s_find_errors :: s_wait_for_bm :: s_correct_errors :: s_outputting :: Nil = Enum(7)
  val state = RegInit(s_idle)

  val received_codeword = Reg(Vec(p.totalSymbolsRS, UInt(p.symbolBits.W)))
  val syndromes = RegInit(VecInit(Seq.fill(p.numParitySymbolsRS)(0.U(p.symbolBits.W))))
  val corrected_symbols = Reg(Vec(p.sourceK, UInt(p.symbolBits.W)))
  val received_count = RegInit(0.U(log2Ceil(p.totalSymbolsRS + 1).W))
  val output_count = RegInit(0.U(log2Ceil(p.sourceK + 1).W))
  val unrecoverable_error = RegInit(false.B)

  val syndrome_mults = Seq.fill(p.numParitySymbolsRS)(Module(new GF256Multiplier(p)))
  val gf_roots = VecInit(GaloisField.alpha_powers.slice(1, p.numParitySymbolsRS + 1).map(_.U(p.symbolBits.W)))
  
  val bm = Module(new BerlekampMassey(p))
  bm.io.start := (state === s_find_errors)
  bm.io.syndromes := syndromes
  
  if(p.sourceK == 223) {
      io.debug_error_count.get := bm.io.error_count_L
  }

  for (i <- 0 until p.numParitySymbolsRS) {
    syndrome_mults(i).io.a := syndromes(i)
    syndrome_mults(i).io.b := gf_roots(i)
  }

  io.in.ready := (state === s_idle)
  io.out.valid := (state === s_outputting)
  io.out.bits.data := corrected_symbols(output_count)
  io.out.bits.last := (output_count === (p.sourceK - 1).U)
  io.error := unrecoverable_error
  io.decoding_done := false.B

  switch(state) {
    is(s_idle) {
      unrecoverable_error := false.B
      received_count := 0.U
      output_count := 0.U
      when(io.in.fire) {
        val first_symbol = io.in.bits.data
        received_codeword(0) := first_symbol
        received_count := 1.U
        for (i <- 0 until p.numParitySymbolsRS) {
          syndromes(i) := first_symbol
        }
        state := s_receiving_and_calc
      }
    }
    is(s_receiving_and_calc) {
      io.in.ready := true.B
      when(io.in.fire) {
        val received_data = io.in.bits.data
        received_codeword(received_count) := received_data
        received_count := received_count + 1.U
        for (i <- 0 until p.numParitySymbolsRS) {
          syndromes(i) := syndrome_mults(i).io.product ^ received_data
        }
        when(io.in.bits.last) {
          state := s_check_syndromes
        }
      }
    }
    is(s_check_syndromes) {
      when(syndromes.reduce(_ | _) === 0.U) {
        state := s_correct_errors
      } .otherwise {
        state := s_find_errors
      }
    }
    is(s_find_errors) {
      state := s_wait_for_bm
    }
    is(s_wait_for_bm) {
        when(bm.io.done){
            when(bm.io.error_count_L > 0.U) {
                unrecoverable_error := true.B
            }
            state := s_correct_errors
        }
    }
    is(s_correct_errors) {
      for (i <- 0 until p.sourceK) {
        corrected_symbols(i) := received_codeword(i)
      }
      state := s_outputting
      output_count := 0.U
    }
    is(s_outputting) {
      io.out.valid := true.B
      when(io.out.fire) {
        val next_output_count = output_count + 1.U
        output_count := next_output_count
        when(next_output_count === p.sourceK.U) {
          state := s_idle
          io.decoding_done := true.B
        }
      }
    }
  }
}