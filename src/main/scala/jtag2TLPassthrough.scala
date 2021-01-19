// SPDX-License-Identifier: Apache-2.0

package jtag2mm

import chisel3._
import chisel3.util._
import chisel3.experimental._
import chisel3.experimental.{withClockAndReset}

import dsptools._
import dsptools.numbers._

import dspblocks._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

import jtag._





trait TLBasicBlock2 extends TLDspBlock with TLHasCSR {
  def csrAddress = AddressSet(0x0, 0xff)
  def beatBytes = 8
  def devname = "tlpassthrough"
  def devcompat = Seq("ucb-art", "dsptools")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  override val mem = Some(TLRegisterNode(address = Seq(csrAddress), device = device, beatBytes = beatBytes))
}

case class PassthroughParams2
(
  depth: Int = 0
) {
  require(depth >= 0, "Passthrough delay must be non-negative")
}

case object PassthroughDepth extends CSRField {
  override val name = "depth"
}

abstract class Passthrough2[D, U, EO, EI, B <: Data](val params: PassthroughParams)(implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] with HasCSR {
  //extends DspBlock[D, U, EO, EI, B] with HasCSR {
  val streamNode = AXI4StreamIdentityNode()
  //val inNode = AXI4StreamSlaveNode(AXI4StreamSlaveParameters())
  //val outNode = AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters(Seq(AXI4StreamMasterParameters("out", n = 8)))))
  
  lazy val module = new LazyModuleImp(this) {
    val (in, _) = streamNode.in.unzip
    val (out, _) = streamNode.out.unzip
    //val ioin = inNode.in(0)._1
    //val in = inNode.in(0)._1
    //val out = outNode.out(0)._1
    //val in = streamNode.in(0)._1
    //val out = streamNode.out(0)._1
    
    val depth = RegInit(UInt(64.W), params.depth.U)
    
    regmap(0x0 -> Seq(RegField(64, depth)))
    
    //out.head <> Queue(in.head, params.depth)
    
    //val ioHelper = Wire(Queue(in.head, params.depth).cloneType)
    //ioHelper <> Queue(in.head, params.depth)
    
    /*out.head.valid := ShiftRegister(in.head.valid, params.depth)
    out.head.bits.data := ShiftRegister(in.head.bits.data, params.depth)
    in.head.ready := out.head.ready*/
    
    /*out.head.valid := ShiftRegister(ioin.valid, params.depth)
    out.head.bits.data := ShiftRegister(ioin.bits.data, params.depth)
    ioin.ready := out.head.ready*/
    out.head.bits.data := depth
    
    //val queue = Module(new Queue(in.cloneType, params.depth))
    //queue.io.enq <> in
    //out <> queue.io.deq
  }
}

class TLPassthrough2(params: PassthroughParams)(implicit p: Parameters)
  extends Passthrough2[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle](params)
    with TLBasicBlock2




class jtag2TLPassthrough (
  irLength: Int,
  initialInstruction: BigInt,
  beatBytes: Int,
  jtagAddresses: AddressSet,
  params: PassthroughParams
)  extends LazyModule()(Parameters.empty) { 

  
  val passthroughModule = LazyModule(new TLPassthrough2(params) {
    
    def standaloneParams = TLBundleParameters(
      addressBits = 16, 
      dataBits = 64,
      sourceBits = 16,
      sinkBits = 16,
      sizeBits = 3,
      aUserBits = 0,
      dUserBits = 0,
      hasBCE = false)
      
    val clientParams = TLClientParameters(
      name = "BundleBridgeToTL",
      sourceId = IdRange(0, 1),
      nodePath = Seq(),
      requestFifo = false,
      visibility = Seq(AddressSet(0, ~0)),
      supportsProbe = TransferSizes(1, beatBytes),
      supportsArithmetic = TransferSizes(1, beatBytes),
      supportsLogical = TransferSizes(1, beatBytes),
      supportsGet = TransferSizes(1, beatBytes),
      supportsPutFull = TransferSizes(1, beatBytes),
      supportsPutPartial = TransferSizes(1, beatBytes),
      supportsHint = TransferSizes(1, beatBytes),
      userBits = Nil)
  
    val ioMem = mem.map { 
      m => {
        val ioMemNode = BundleBridgeSource(() => TLBundle(standaloneParams))
        m := BundleBridgeToTL(TLClientPortParameters(Seq(clientParams))) := ioMemNode
        val ioMem = InModuleBody { ioMemNode.makeIO() }
        ioMem
      }
    }
    
    val ioStreamNode = BundleBridgeSink[AXI4StreamBundle]()
    ioStreamNode := 
    AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) := streamNode
    val outStream = InModuleBody { ioStreamNode.makeIO() }
    
    val ioparallelin = BundleBridgeSource(() => new AXI4StreamBundle(AXI4StreamBundleParameters(n = 8)))
    streamNode := BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 8)) := ioparallelin
    //inNode := BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 8)) := ioparallelin
    val inStream2 = InModuleBody { ioparallelin.makeIO() }
    
    /*val ioIn = BundleBridgeSource(() => new AXI4StreamBundle(AXI4StreamBundleParameters(n = 8)))
    inNode := BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 8)) := ioIn
    //inNode := BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 8)) := ioparallelin
    val inStream = InModuleBody { ioIn.makeIO() }*/
    
  })
  
  val jtagModule = LazyModule(new TLJTAGToMasterBlock(irLength, initialInstruction, beatBytes, jtagAddresses))
  
  InModuleBody { passthroughModule.ioMem.get <> jtagModule.ioTL }

  
  def makeIO1(): AXI4StreamBundle = {
    val io2: AXI4StreamBundle = IO(passthroughModule.outStream.cloneType)
    io2.suggestName("outStream")
    io2 <> passthroughModule.outStream
    io2
  }
  def makeIO2(): topModuleIO = {
    val io2: topModuleIO = IO(jtagModule.ioJTAG.cloneType)
    io2.suggestName("ioJTAG")
    io2 <> jtagModule.ioJTAG
    io2
  }
  /*def makeIO3(): AXI4StreamBundle = {
    val io2: AXI4StreamBundle = IO(passthroughModule.inStream.cloneType)
    io2.suggestName("inStream")
    io2 <> passthroughModule.inStream
    io2
  }*/

  val outStream = InModuleBody { makeIO1() }
  val ioJTAG = InModuleBody { makeIO2() }
  //val inStream = InModuleBody { makeIO3() }

  lazy val module = new LazyModuleImp(this)

}


object JTAGToTLPassthroughApp extends App
{
    
    val params = PassthroughParams(depth = 0)
    val irLength = 3
    val initialInstruction = BigInt("0", 2)
    val addresses = AddressSet(0x00000, 0x3FFF)
    val beatBytes = 8
  
  implicit val p: Parameters = Parameters.empty
  val jtagModule = LazyModule(new jtag2TLPassthrough(irLength, initialInstruction, beatBytes, addresses, params))
  
  chisel3.Driver.execute(args, ()=> jtagModule.module)
}

