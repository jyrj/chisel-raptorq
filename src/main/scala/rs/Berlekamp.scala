package rs
import chisel3._
import chisel3.util._

/** Sequential Berlekamp-Massey working on one syndrome per clock.
  * Algorithm identical to RFC 6330 appendix but expressed in hardware.
  *
  *   – p.redundancy = 32 steps  (RS(255,223))
  *   – outputs Λ(x) coeffs λ0…λt   (λ0 == 1)
  *   – degree L   (0…t)
  *   – .valid pulses high 1-cycle when Λ ready.
  */
class Berlekamp(p: RsParams) extends Module {
  private val T = p.redundancy / 2

  val io = IO(new Bundle {
    val syndIn = Input(Valid(UInt(p.m.W)))     // present S_k in ascending order
    val locOut = Valid(new Bundle {
      val coeff = Vec(T + 1, UInt(p.m.W))      // Λ0..ΛT   (higher coeffs == 0)
      val deg   = UInt(6.W)                    // 0…16
    })
  })

  /* Registers holding the state vectors */
  val Λ  = RegInit(VecInit(1.U(p.m.W) +: Seq.fill(T)(0.U(p.m.W)))) // current locator
  val B  = RegInit(VecInit(1.U(p.m.W) +: Seq.fill(T)(0.U(p.m.W)))) // “backup” poly
  val L  = RegInit(0.U(6.W))                                       // current degree
  val k  = RegInit(0.U(6.W))                                       // iteration index

  /* δ_k = S_k ⊕ Σ_{i=1..L} λ_i · S_{k-i} */
  val deltaTerms = Wire(Vec(T, UInt(p.m.W)))
  for(i <- 0 until T) {
    val idxValid = i.U < L
    val contrib  = GF256.mul(Λ(i+1), ShiftRegister(io.syndIn.bits, i+1))
    deltaTerms(i) := Mux(idxValid, contrib, 0.U)
  }
  val delta = deltaTerms.reduce(_ ^ _) ^ io.syndIn.bits

  /* BM core update */
  val Λ_next  = Wire(Vec(T+1, UInt(p.m.W)))
  val B_next  = Wire(Vec(T+1, UInt(p.m.W)))
  val L_next  = Wire(UInt(6.W))

  when(io.syndIn.valid) {
    /* default: just advance B ← x·B */
    B_next(0) := 0.U
    for(i <- 1 to T) B_next(i) := B(i-1)

    when(delta =/= 0.U) {
      val scale = GF256.div(delta, B(0))
      /* Λ ← Λ ⊕ scale·x·B   */
      Λ_next(0) := Λ(0)
      for(i <- 1 to T) {
        val scaled = GF256.mul(scale, B(i-1))
        Λ_next(i)  := Λ(i) ^ scaled
      }

      when((k << 1) >= L) {          // update L and B
        L_next := k + 1.U - L
        for(i <- 0 to T) B_next(i) := Λ(i)
      }.otherwise {
        L_next := L
      }
    }.otherwise {                    // delta==0 → only shift B
      Λ_next := Λ
      L_next := L
    }

    /* commit registers */
    Λ := Λ_next
    B := B_next
    L := L_next
    k := k + 1.U
  }

  /* valid when all 32 syndromes consumed */
  val done = RegNext(io.syndIn.valid && (k === (p.redundancy-1).U), false.B)
  io.locOut.valid := done
  io.locOut.bits.coeff := Λ
  io.locOut.bits.deg   := L
}