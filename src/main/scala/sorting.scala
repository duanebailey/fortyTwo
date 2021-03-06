/**
 * Sorting accelerators.  These are stateless accelerators that exchange
 * bytes, words, or longwords so that the smallest values are to the right
 * (ie. use the least numbered bits).
 */
package williams

// Not sure how many of these *really* need to be imported
import Chisel._
import freechips.rocketchip.tile._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheReq}

/**
 * Compare and exchange two bytes.
 * inputs: io.a and io.b
 * outputs: io.small and io.large
 */
class CoEx8 extends Module {
  val io = IO(new Bundle{
     val a = Input(UInt(8.W))
     val b = Input(UInt(8.W))
     val small = Output(UInt(8.W))
     val large = Output(UInt(8.W))
  })
  val inOrder = io.a <= io.b
  io.small := Mux(inOrder,io.a,io.b)
  io.large := Mux(inOrder,io.b,io.a)
}

/**
 * Factory method for compare-exchange 8-bit values.
 */
object CoEx8 {
   def apply() = {
      Module(new CoEx8())
   }
}

/**
 * Compare and exchange two shorts.
 * inputs: io.a and io.b
 * outputs: io.small and io.large
 */
class CoEx16 extends Module {
  val io = IO(new Bundle{
     val a = Input(UInt(16.W))
     val b = Input(UInt(16.W))
     val small = Output(UInt(16.W))
     val large = Output(UInt(16.W))
  })
  val inOrder = io.a <= io.b
  io.small := Mux(inOrder,io.a,io.b)
  io.large := Mux(inOrder,io.b,io.a)
}

/**
 * Factory method for compare-exchange 16-bit values.
 */
object CoEx16 {
   def apply() = {
      Module(new CoEx16())
   }
}

/**
 * Compare and exchange two words.
 * inputs: io.a and io.b
 * outputs: io.small and io.large
 */
class CoEx32 extends Module {
  val io = IO(new Bundle{
     val a = Input(UInt(32.W))
     val b = Input(UInt(32.W))
     val small = Output(UInt(32.W))
     val large = Output(UInt(32.W))
  })
  val inOrder = io.a <= io.b
  io.small := Mux(inOrder,io.a,io.b)
  io.large := Mux(inOrder,io.b,io.a)
}

/**
 * Factory method for compare-exchange 32-bit values.
 */
object CoEx32 {
   def apply() = {
      Module(new CoEx32())
   }
}

/**
 * Accelerator for sorting the 8 bytes of a long word.
 * 
 */
class ByteSort(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
   override lazy val module = new ByteSortImp(this)
}

class WithByteSort extends Config ((site, here, up) => {
  case BuildRoCC => Seq((p: Parameters) => {
     val bs = LazyModule.apply(new ByteSort(OpcodeSet.custom0)(p))
     bs
  })
})

/**
 * This accelerator sorts the 8 bytes of a long word.
 * This is a stateless accelerator that does not reference memory.
 */
class ByteSortImp(outer: ByteSort)(implicit p: Parameters)
   extends LazyRoCCModuleImp(outer)
{
   // State is busy precisely while the instruction is executing; else ready.
   val s_ready :: s_busy :: Nil = Enum(Bits(), 2)
   val state = Reg(init = s_ready)   // the starting state
   val req_rd = Reg(io.resp.bits.rd) // the destination register
   // grab the bytes of the input long word
   val b0 = Reg(init = 0.U(8.W)) // lower byte
   val b1 = Reg(init = 0.U(8.W)) // higher byte
   val b2 = Reg(init = 0.U(8.W))
   val b3 = Reg(init = 0.U(8.W))
   val b4 = Reg(init = 0.U(8.W))
   val b5 = Reg(init = 0.U(8.W))
   val b6 = Reg(init = 0.U(8.W))
   val b7 = Reg(init = 0.U(8.W))

   // we're ready for a new command when state is ready.
   io.cmd.ready := (state === s_ready)
   // unit is busy when we're not in the ready state
   io.busy := (state =/= s_ready)

   // when command fires, grab the destination register and source values
   when (io.cmd.fire()) {
      req_rd := io.cmd.bits.inst.rd    // destination register number
      b0 := io.cmd.bits.rs1(7,0)  // source low byte
      b1 := io.cmd.bits.rs1(15,8)// source high byte
      b2 := io.cmd.bits.rs1(23,16)// source high byte
      b3 := io.cmd.bits.rs1(31,24)// source high byte
      b4 := io.cmd.bits.rs1(39,32)// source high byte
      b5 := io.cmd.bits.rs1(47,40)// source high byte
      b6 := io.cmd.bits.rs1(55,48)// source high byte
      b7 := io.cmd.bits.rs1(63,56)// source high byte
      state := s_busy                  // indicate we're busy.
   }

   // stage 0
   val s00 = CoEx8()
   s00.io.a := b0
   s00.io.b := b1
   val s01 = CoEx8()
   s01.io.a := b2
   s01.io.b := b3
   val s02 = CoEx8()
   s02.io.a := b4
   s02.io.b := b5
   val s03 = CoEx8()
   s03.io.a := b6
   s03.io.b := b7

   val s10 = CoEx8()
   s10.io.a := s00.io.small
   s10.io.b := s01.io.large
   val s11 = CoEx8()
   s11.io.a := s00.io.large
   s11.io.b := s01.io.small
   val s12 = CoEx8()
   s12.io.a := s02.io.small
   s12.io.b := s03.io.large
   val s13 = CoEx8()
   s13.io.a := s02.io.large
   s13.io.b := s03.io.small

   val s20 = CoEx8()
   s20.io.a := s10.io.small
   s20.io.b := s11.io.small
   val s21 = CoEx8()
   s21.io.a := s10.io.large
   s21.io.b := s11.io.large
   val s22 = CoEx8()
   s22.io.a := s12.io.large
   s22.io.b := s13.io.large
   val s23 = CoEx8()
   s23.io.a := s12.io.small
   s23.io.b := s13.io.small

   val s30 = CoEx8()
   s30.io.a := s20.io.small
   s30.io.b := s22.io.large
   val s31 = CoEx8()
   s31.io.a := s20.io.large
   s31.io.b := s22.io.small
   val s32 = CoEx8()
   s32.io.a := s21.io.small
   s32.io.b := s23.io.large
   val s33 = CoEx8()
   s33.io.a := s21.io.large
   s33.io.b := s23.io.small

   val s40 = CoEx8()
   s40.io.a := s30.io.small
   s40.io.b := s32.io.small
   val s41 = CoEx8()
   s41.io.a := s31.io.small
   s41.io.b := s33.io.small
   val s42 = CoEx8()
   s42.io.a := s30.io.large
   s42.io.b := s32.io.large
   val s43 = CoEx8()
   s43.io.a := s31.io.large
   s43.io.b := s33.io.large

   val s50 = CoEx8()
   s50.io.a := s40.io.small
   s50.io.b := s41.io.small
   val s51 = CoEx8()
   s51.io.a := s40.io.large
   s51.io.b := s41.io.large
   val s52 = CoEx8()
   s52.io.a := s42.io.small
   s52.io.b := s43.io.small
   val s53 = CoEx8()
   s53.io.a := s42.io.large
   s53.io.b := s43.io.large

    // develop response: set reg # and value
   io.resp.bits.data := Cat(s53.io.large,s53.io.small,
                            s52.io.large,s52.io.small,
                            s51.io.large,s51.io.small,
                            s50.io.large,s50.io.small)
   io.resp.bits.rd := req_rd                           // to be written here
   io.resp.valid := (state === s_busy)                 // we're done

   // when we respond, become idle again
   when (io.resp.fire()) {
     state := s_ready
   }

   io.interrupt := Bool(false)     // instructions never interrupt
   io.mem.req.valid := Bool(false)  // we don't use memory
}


class CompExchange(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new CompExchangeImp(this)
}

class WithCompExchange extends Config ((site, here, up) => {
  case BuildRoCC => Seq((p: Parameters) => {
     val cex = LazyModule.apply(new CompExchange(OpcodeSet.custom0)(p))
     cex
  })
})

/**
 * This accelerator compares the two low bytes of the first source
 * register and exchanges them if they are out-of-order.
 * After this instruction, the least significant byte (bits 7..0) is
 * no greater than the next least significant byte (bits 15..8).
 * This is a stateless accelerator that does not reference memory.
 */
class CompExchangeImp(outer: CompExchange)(implicit p: Parameters)
   extends LazyRoCCModuleImp(outer)
{
   // State is busy precisely while the instruction is executing; else ready.
   val s_ready :: s_busy :: Nil = Enum(Bits(), 2)
   val state = Reg(init = s_ready)   // the starting state
   val req_rd = Reg(io.resp.bits.rd) // the destination register
   val lowByte = Reg(init = 0.U(8.W)) // lower byte
   val highByte = Reg(init = 0.U(8.W)) // higher byte

   // we're ready for a new command when state is ready.
   io.cmd.ready := (state === s_ready)
   // unit is busy when we're not in the ready state
   io.busy := (state =/= s_ready)

   // when command fires, grab the destination register and source values
   when (io.cmd.fire()) {
      req_rd := io.cmd.bits.inst.rd    // destination register number
      lowByte := io.cmd.bits.rs1(7,0)  // source low byte
      highByte := io.cmd.bits.rs1(15,8)// source high byte
      state := s_busy                  // indicate we're busy.
   }

   // combinational ordering of the two bytes
   val cex = Module(new CoEx8())
   cex.io.a := lowByte
   cex.io.b := highByte
   val smaller = cex.io.small 
   val bigger = cex.io.large
   //val leq = lowByte <= highByte
   //val bigger = Mux(leq,highByte,lowByte)
   //val smaller = Mux(leq,lowByte,highByte)

   // develop response: set reg # and value
   io.resp.bits.data := Cat(0.U(48.W),bigger,smaller)  // 64-bit response
   io.resp.bits.rd := req_rd                           // to be written here
   io.resp.valid := (state === s_busy)                 // we're done

   // when we respond, become idle again
   when (io.resp.fire()) {
     state := s_ready
   }

   io.interrupt := Bool(false)     // instructions never interrupt
   io.mem.req.valid := Bool(false)  // we don't use memory
}
