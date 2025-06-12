package rs
import chisel3._
import chisel3.util._

/** 1-symbol/clk syndrome generator (Horner form).            */
class Syndrome(p: RsParams) extends Module {
  val io = IO(new Bundle{
    val in   = Input(Valid(UInt(p.m.W)))   // next received symbol
    val last = Input(Bool())               // true with final symbol
    val out  = Valid(Vec(p.redundancy, UInt(p.m.W)))
  })

  /* constant roots α^(FCR+k)  – FCR = 0 in RS(255,223) */
/* constant roots α^(k)  (FCR = 0) */
private val roots = VecInit((0 until p.redundancy).map(k => GF256.alphaConst(k)))
  /* running accumulators */
  val acc = RegInit(VecInit(Seq.fill(p.redundancy)(0.U(p.m.W))))

  val next = Wire(Vec(p.redundancy, UInt(p.m.W)))
  for(k <- 0 until p.redundancy)
    next(k) := GF256.mul(acc(k), roots(k)) ^ io.in.bits

  when(io.in.valid) {
    acc := Mux(io.last, 0.U.asTypeOf(acc), next)
  }

  /* output result one cycle after last symbol */
  val outReg   = Reg(Vec(p.redundancy, UInt(p.m.W)))
  val outValid = RegInit(false.B)

  when(io.in.valid && io.last) {
    outReg   := next
    outValid := true.B
  }.otherwise{
    outValid := false.B
  }

  io.out.bits  := outReg
  io.out.valid := outValid
}