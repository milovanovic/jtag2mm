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
import nco._

class jtag2Axi4NCO[T <: Data: Real: BinaryRepresentation](
  irLength:           Int,
  initialInstruction: BigInt,
  beatBytes:          Int,
  jtagAddresses:      AddressSet,
  maxBurstNum:        Int,
  paramsNCO:          NCOParams[T],
  csrAddressNCO:      AddressSet)
    extends LazyModule()(Parameters.empty) {

  trait AXI4Block
      extends DspBlock[
        AXI4MasterPortParameters,
        AXI4SlavePortParameters,
        AXI4EdgeParameters,
        AXI4EdgeParameters,
        AXI4Bundle
      ] {
    def standaloneParams = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 1)
    val ioMem = mem.map { m =>
      {
        val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))
        m := BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) := ioMemNode
        val ioMem = InModuleBody { ioMemNode.makeIO() }
        ioMem
      }
    }
    // generate out stream
    val ioStreamNode = BundleBridgeSink[AXI4StreamBundle]()
    ioStreamNode :=
      AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) := streamNode
    val outStream = InModuleBody { ioStreamNode.makeIO() }
  }

  val ncoModule = LazyModule(
    new AXI4NCOLazyModuleBlock(paramsNCO, AddressSet(0x000000, 0xff), beatBytes = beatBytes) with AXI4Block
  )

  val jtagModule = LazyModule(
    new AXI4JTAGToMasterBlock(irLength, initialInstruction, beatBytes, jtagAddresses, maxBurstNum)
  )

  //ncoModule.mem.get := jtagModule.node.get
  InModuleBody { ncoModule.ioMem.get <> jtagModule.ioAXI4 }

  def makeIO1(): AXI4StreamBundle = {
    val io2: AXI4StreamBundle = IO(ncoModule.outStream.cloneType)
    io2.suggestName("outStream")
    io2 <> ncoModule.outStream
    io2
  }
  def makeIO2(): topModuleIO = {
    val io2: topModuleIO = IO(jtagModule.ioJTAG.cloneType)
    io2.suggestName("ioJTAG")
    io2 <> jtagModule.ioJTAG
    io2
  }

  val outStream = InModuleBody { makeIO1() }
  val ioJTAG = InModuleBody { makeIO2() }

  lazy val module = new LazyModuleImp(this)

}

object JTAGToAxi4NCOApp extends App {

  val paramsNCO = FixedNCOParams(
    tableSize = 64,
    tableWidth = 16,
    phaseWidth = 8,
    rasterizedMode = false,
    nInterpolationTerms = 0,
    ditherEnable = false,
    syncROMEnable = false,
    phaseAccEnable = true,
    roundingMode = RoundHalfUp,
    pincType = Config,
    poffType = Fixed,
    useMultiplier = true,
    numMulPipes = 1,
    useQAM = false
  )
  val beatBytes = 4

  implicit val p: Parameters = Parameters.empty
  val jtagModule = LazyModule(
    new jtag2Axi4NCO(3, BigInt("0", 2), 4, AddressSet(0x00000, 0x3fff), 8, paramsNCO, AddressSet(0x0000, 0x00ff))
  )

  chisel3.Driver.execute(args, () => jtagModule.module)
}
