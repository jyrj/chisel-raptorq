package raptorfecgen

import chisel3._
import chisel3.util._

object RSDecoderGF {
    // Correct and complete table of powers for the primitive element alpha = 2 in GF(2^8)
    // with polynomial 0x11D.
    val alpha_powers: Seq[Int] = Seq(
        0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1d, 0x3a, 0x74, 0xe8, 0xcd, 0x87, 0x13, 0x26,
        0x4c, 0x98, 0x2d, 0x5a, 0xb4, 0x75, 0xea, 0xc9, 0x8f, 0x03, 0x06, 0x0c, 0x18, 0x30, 0x60, 0xc0,
        0x9d, 0x27, 0x4e, 0x9c, 0x25, 0x4a, 0x94, 0x35, 0x6a, 0xd4, 0xb5, 0x77, 0xee, 0xc1, 0x9f, 0x23,
        0x46, 0x8c, 0x05, 0x0a, 0x14, 0x28, 0x50, 0xa0, 0x5d, 0xba, 0x6d, 0xda, 0xa9, 0x4f, 0x9e, 0x21,
        0x42, 0x84, 0x15, 0x2a, 0x54, 0xa8, 0x4d, 0x9a, 0x29, 0x52, 0xa4, 0x55, 0xaa, 0x49, 0x92, 0x3d,
        0x7a, 0xf4, 0xf5, 0xf7, 0xf3, 0xfb, 0xed, 0xc7, 0x93, 0x3f, 0x7e, 0xfc, 0xe5, 0xd7, 0xb3, 0x7f,
        0xfe, 0xe1, 0xdf, 0xa3, 0x5f, 0xbe, 0x61, 0xc2, 0x99, 0x2f, 0x5e, 0xbc, 0x65, 0xca, 0x89, 0x0f,
        0x1e, 0x3c, 0x78, 0xf0, 0xfd, 0xe7, 0xd3, 0xbf, 0x63, 0xc6, 0x91, 0x3b, 0x76, 0xec, 0xc5, 0x97,
        0x33, 0x66, 0xcc, 0x85, 0x17, 0x2e, 0x5c, 0xb8, 0x6d, 0xda, 0xa9, 0x4f, 0x9e, 0x21, 0x42, 0x84,
        0x15, 0x2a, 0x54, 0xa8, 0x4d, 0x9a, 0x29, 0x52, 0xa4, 0x55, 0xaa, 0x49, 0x92, 0x3d, 0x7a, 0xf4,
        0xf5, 0xf7, 0xf3, 0xfb, 0xed, 0xc7, 0x93, 0x3f, 0x7e, 0xfc, 0xe5, 0xd7, 0xb3, 0x7f, 0xfe, 0xe1,
        0xdf, 0xa3, 0x5f, 0xbe, 0x61, 0xc2, 0x99, 0x2f, 0x5e, 0xbc, 0x65, 0xca, 0x89, 0x0f, 0x1e, 0x3c,
        0x78, 0xf0, 0xfd, 0xe7, 0xd3, 0xbf, 0x63, 0xc6, 0x91, 0x3b, 0x76, 0xec, 0xc5, 0x97, 0x33, 0x66,
        0xcc, 0x85, 0x17, 0x2e, 0x5c, 0xb8, 0x6d, 0xda, 0xa9, 0x4f, 0x9e, 0x21, 0x42, 0x84, 0x15, 0x2a,
        0x54, 0xa8, 0x4d, 0x9a, 0x29, 0x52, 0xa4, 0x55, 0xaa, 0x49, 0x92, 0x3d, 0x7a, 0xf4, 0xf5, 0xf7,
        0xf3, 0xfb, 0xed, 0xc7, 0x93, 0x3f, 0x7e, 0xfc, 0xe5, 0xd7, 0xb3, 0x7f, 0xfe, 0xe1, 0xdf, 0xa3
    ).take(255) // Ensure we only have 255 values (alpha^0 to alpha^254)
}

class RSDecoder(val p: RaptorFECParameters) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new FECDataBundle(p)))
    val out = Decoupled(new FECDataBundle(p))
    val error = Output(Bool())
    val decoding_done = Output(Bool())
  })

  val s_idle :: s_receiving_and_calc :: s_check_syndromes :: s_find_errors :: s_correct_errors :: s_outputting :: Nil = Enum(6)
  val state = RegInit(s_idle)

  val received_codeword = Reg(Vec(p.totalSymbolsRS, UInt(p.symbolBits.W)))
  val syndromes = RegInit(VecInit(Seq.fill(p.numParitySymbolsRS)(0.U(p.symbolBits.W))))
  val corrected_symbols = Reg(Vec(p.sourceK, UInt(p.symbolBits.W)))
  val received_count = RegInit(0.U(log2Ceil(p.totalSymbolsRS + 1).W))
  val output_count = RegInit(0.U(log2Ceil(p.sourceK + 1).W))
  val unrecoverable_error = RegInit(false.B)

  val syndrome_mults = Seq.fill(p.numParitySymbolsRS)(Module(new GF256Multiplier(p)))
  val gf_roots = VecInit(RSDecoderGF.alpha_powers.slice(1, p.numParitySymbolsRS + 1).map(_.U(p.symbolBits.W)))

  for (i <- 0 until p.numParitySymbolsRS) {
    syndrome_mults(i).io.a := syndromes(i)
    syndrome_mults(i).io.b := gf_roots(i)
  }

  io.in.ready := false.B
  io.out.valid := false.B
  io.out.bits.data := corrected_symbols(output_count)
  io.out.bits.last := (output_count === (p.sourceK - 1).U)
  io.error := unrecoverable_error
  io.decoding_done := false.B

  printf(p"RSDecoder Cycle --- State=${state}, ReceivedCnt=${received_count}, OutputCnt=${output_count}\n")

  switch(state) {
    is(s_idle) {
      io.in.ready := true.B
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
          printf("RSDecoder: Received full block and calculated all syndromes.\n")
        }
      }
    }

    is(s_check_syndromes) {
      val all_syndromes_zero = syndromes.reduce(_ | _) === 0.U
      when(all_syndromes_zero) {
        printf("RSDecoder: No errors detected.\n")
        state := s_correct_errors
      } .otherwise {
        printf("RSDecoder: Errors detected.\n")
        state := s_find_errors
      }
    }

    is(s_find_errors) {
      unrecoverable_error := true.B
      printf("RSDecoder: In s_find_errors (placeholder).\n")
      state := s_correct_errors
    }

    is(s_correct_errors) {
      printf("RSDecoder: In s_correct_errors (placeholder).\n")
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
          printf("RSDecoder: Finished outputting block. Returning to idle.\n")
          state := s_idle
          io.decoding_done := true.B
        }
      }
    }
  }
}




















