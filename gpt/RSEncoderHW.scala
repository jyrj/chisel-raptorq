package raptorq
import chisel3._
import chisel3.util._

/** RS(255,223) streaming encoder – Week-1 milestone.
  * • 1 symbol / cycle, no internal FIFOs
  * • requires codeOut.ready to be High (true in our unit test)
  */
class RSEncoderHW(p: RaptorParams = RaptorParams()) extends Module {
  require(p.symbolBits == 8, "Week-1: GF(256) only")
  require(p.streamWidth == 1, "Week-1: streamWidth must be 1")

  /*----------------------------------------------------------------------*/
  /*  I / O                                                               */
  /*----------------------------------------------------------------------*/
  val io = IO(new Bundle{
    val dataIn  = Flipped(Decoupled(UInt(8.W)))  // 223 data symbols
    val codeOut = Decoupled(UInt(8.W))           // 255 data+parity
    val start   = Input(Bool())                  // pulse to begin block
  })

  /*----------------------------------------------------------------------*/
  /*  Generator polynomial (g0 … g31)                                      */
  /*----------------------------------------------------------------------*/
  def polyMul(a: Seq[Int], b: Seq[Int]): Seq[Int] = {
    val res = Array.fill(a.length + b.length - 1)(0)
    for(i <- a.indices; j <- b.indices)
      res(i+j) = GF256.add(res(i+j), GF256.mul(a(i), b(j)))
    res.toSeq
  }
  val t        = p.repairCap                          // 32 parity bytes
  val gFull    = (0 until t).foldLeft(Seq(1)){ (gp,i) =>
                  polyMul(gp, Seq(1, GF256.pow(2,i))) }
  val gCoeff   = gFull.take(t)                        // drop leading 1
  val mulLUTs  = gCoeff.map { c =>
                   VecInit((0 until 256).map(b => GF256.mul(c,b).U(8.W)))
                 }

  /*----------------------------------------------------------------------*/
  /*  Parity LFSR registers                                                */
  /*----------------------------------------------------------------------*/
  val parity = RegInit(VecInit(Seq.fill(t)(0.U(8.W))))

  /*----------------------------------------------------------------------*/
  /*  FSM                                                                  */
  /*----------------------------------------------------------------------*/
  val sIdle :: sData :: sParity :: Nil = Enum(3)
  val state     = RegInit(sIdle)
  val dataCnt   = RegInit(0.U(8.W))      // counts 0 … 223
  val parityCnt = RegInit(0.U(6.W))      // counts 0 … 31

  /* defaults */
  io.dataIn.ready  := false.B
  io.codeOut.valid := false.B
  io.codeOut.bits  := 0.U

  switch(state) {

    /*------------------  IDLE  ------------------*/
    is(sIdle) {
      when (io.start) {
        parity.foreach(_ := 0.U)
        dataCnt   := 0.U
        parityCnt := 0.U
        state     := sData
      }
    }

/*------------------  DATA  ------------------*/
is(sData) {
  io.dataIn.ready  := true.B                        // <-- always ready
  io.codeOut.valid := io.dataIn.valid               // forward data
  io.codeOut.bits  := io.dataIn.bits

  when(io.dataIn.fire) {
    val fb = io.dataIn.bits ^ parity.last
    for(i <- (t-1) to 1 by -1)
      parity(i) := parity(i-1) ^ mulLUTs(i)(fb)
    parity(0) := mulLUTs(0)(fb)

    val nextCnt = dataCnt + 1.U
    dataCnt := nextCnt
    when(nextCnt === p.sourceK.U) {                 // 223 symbols
      state     := sParity
      parityCnt := 0.U
    }
  }
}


    /*------------------  PARITY  ------------------*/
    is(sParity) {
      io.codeOut.valid := true.B
      io.codeOut.bits  := parity(parityCnt)

      when (io.codeOut.ready) {
        val nextP = parityCnt + 1.U
        parityCnt := nextP
        when(nextP === p.repairCap.U) {       // 32 parity emitted
          state := sIdle
        }
      }
    }
  }
}
