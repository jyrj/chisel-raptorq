package rs
import chisel3._
import chisel3.util._

/** One-root-per-cycle Chien search.
  *   – streams α⁰ … α^{n-1} through Λ(x)
  *   – collects up to T roots and returns their positions.
  */
class Chien(p: RsParams) extends Module {
  val io = IO(new Bundle {
    val locIn  = Input(Valid(Vec(p.t + 1, UInt(p.m.W))))   // Λ₀…Λ_T (Λ₀ = 1)
    val errPos = Valid(new VecFfsIf(p.t, 8))
  })

  /* ----------------------------------------------------------------
     Capture locator polynomial                                       */
  val Λ = Reg(Vec(p.t + 1, UInt(p.m.W)))
  val start = io.locIn.valid
  when(start) { Λ := io.locIn.bits }

  /* ----------------------------------------------------------------
     Run through all field elements                                   */
  val idx   = RegInit(0.U(9.W))           // 0 … 254
  val run   = RegInit(false.B)
  when(start)               { run := true.B; idx := 0.U }
  when(run)                 { idx := idx + 1.U
                               when(idx === (p.n-1).U) { run := false.B } }

  val x  = GF256.alpha(idx)
  /* Horner evaluation  Λ(x)  */
  val horner = Λ.reverse.foldLeft(0.U(p.m.W))((acc, c) => GF256.mul(acc, x) ^ c)

  /* ----------------------------------------------------------------
     Collect roots                                                    */
  val collect = Reg(Vec(p.t, UInt(8.W)))
  val ffs     = RegInit(0.U(p.t.W))

  when(start) { ffs := 0.U }               // clear between code-words
  when(run && horner === 0.U) {
    val sel = PopCount(ffs)
    when(sel < p.t.U) {
      collect(sel) := p.n.U - 1.U - idx     // convert to symbol index
      ffs := ffs | (1.U << sel)
    }
  }

  /* ----------------------------------------------------------------
     Output one cycle after finishing last root                       */
  val done = RegNext(run && (idx === (p.n-1).U), init = false.B)
  io.errPos.valid     := done
  io.errPos.bits.vec  := collect
  io.errPos.bits.ffs  := ffs
}