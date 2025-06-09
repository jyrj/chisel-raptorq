package raptorfecgen

import chisel3._
import chisel3.util._

/** Serial (1‑symbol/clk) Chien search over GF(2⁸).
  *
  *   • Scans x = 1, α⁻¹, α⁻² … α⁻(N−1)
  *   • Marks position *i* if σ(x)=0
  */
class ChienSearch(val p: RaptorFECParameters) extends Module {
  private val N         = p.totalSymbolsRS
  private val t         = p.numParitySymbolsRS / 2
  private val polyWidth = t + 1                 // σ₀ … σ_t

  val io = IO(new Bundle {
    val start     = Input(Bool())
    val sigma     = Input(Vec(polyWidth, UInt(8.W)))   // locator coeffs
    val busy      = Output(Bool())
    val done      = Output(Bool())
    val rootMask  = Output(Vec(N, Bool()))
  })

  /* ---------------- constants ---------------- */
  private val alphaInv = GaloisField.alpha_powers(254).U(8.W) // α^-1
  private val multEval = Seq.fill(polyWidth - 1)(Module(new GF256Multiplier(p)))
  private val multStep = Module(new GF256Multiplier(p))

  // give all multipliers safe defaults so FIRRTL is happy
  (multEval :+ multStep).foreach { m => m.io.a := 0.U; m.io.b := 0.U }

  /* ---------------- state -------------------- */
  val xReg   = RegInit(1.U(8.W))                         // current α^{-i}
  val idx    = RegInit(0.U(log2Ceil(N).W))
  val roots  = RegInit(VecInit(Seq.fill(N)(false.B)))

  val sIdle :: sRun :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)

  io.busy     := (state === sRun)
  io.done     := (state === sDone)
  io.rootMask := roots

  /* -------- one‑cycle Horner evaluation ------- */
  val stage = Wire(Vec(polyWidth, UInt(8.W)))
  stage(polyWidth - 1) := io.sigma(polyWidth - 1)
  for (j <- (0 until polyWidth - 1).reverse) {
    multEval(j).io.a := stage(j + 1)
    multEval(j).io.b := xReg
    stage(j)         := multEval(j).io.product ^ io.sigma(j)
  }
  val polyAtX = stage(0)

  /* ---------------- FSM ---------------------- */
  switch(state) {
    is(sIdle) {
      when(io.start) {
        xReg := 1.U
        idx  := 0.U
        roots.foreach(_ := false.B)
        state := sRun
      }
    }

    is(sRun) {
      roots(idx) := (polyAtX === 0.U)

      when(idx === (N - 1).U) {
        state := sDone
      }.otherwise {
        multStep.io.a := xReg
        multStep.io.b := alphaInv       // multiply by α^-1
        xReg := multStep.io.product
        idx  := idx + 1.U
      }
    }

    is(sDone) { state := sIdle }
  }
}
