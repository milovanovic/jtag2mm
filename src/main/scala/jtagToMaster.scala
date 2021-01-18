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


class JtagToMasterControllerIO(irLength: Int, beatBytes: Int) extends JtagBlockIO(irLength) {
  val dataOut = Output(UInt((beatBytes*8).W))
}

class topModuleIO extends Bundle {
  val jtag = new JtagIO
  val asyncReset = Input(Bool())
}


class JtagController(irLength: Int, initialInstruction: BigInt, beatBytes: Int) extends Module {
  require(irLength >= 2)

  val io = IO(new JtagToMasterControllerIO(irLength, beatBytes))
  
  val tdo = Wire(Bool())
  tdo := DontCare
  val tdo_driven = Wire(Bool())
  tdo_driven := DontCare
  io.jtag.TDO.data := NegativeEdgeLatch(clock, tdo)
  io.jtag.TDO.driven := NegativeEdgeLatch(clock, tdo_driven)
  
  val stateMachine = Module(new JtagStateMachine)
  stateMachine.io.tms := io.jtag.TMS
  val currState = stateMachine.io.currState
  io.output.state := stateMachine.io.currState
  stateMachine.io.asyncReset := io.control.fsmAsyncReset
  
  val irShifter = Module(CaptureUpdateChain(UInt(irLength.W)))
  irShifter.io.chainIn.shift := currState === JtagState.ShiftIR.U
  irShifter.io.chainIn.data := io.jtag.TDI
  irShifter.io.chainIn.capture := currState === JtagState.CaptureIR.U
  irShifter.io.chainIn.update := currState === JtagState.UpdateIR.U
  irShifter.io.capture.bits := "b01".U

  val updateInstruction = Wire(Bool())
  updateInstruction := DontCare

  val nextActiveInstruction = Wire(UInt(irLength.W))
  nextActiveInstruction := DontCare

  val activeInstruction = NegativeEdgeLatch(clock, nextActiveInstruction, updateInstruction)

  when (reset.asBool) {
    nextActiveInstruction := initialInstruction.U(irLength.W)
    updateInstruction := true.B
  } .elsewhen (currState === JtagState.UpdateIR.U) {
    nextActiveInstruction := irShifter.io.update.bits
    updateInstruction := true.B
  } .otherwise {
    updateInstruction := false.B
  }
  io.output.instruction := activeInstruction

  io.output.reset := currState === JtagState.TestLogicReset.U
  
  val drShifter = Module(CaptureUpdateChain(UInt((beatBytes*8).W)))
  drShifter.io.chainIn.shift := currState === JtagState.ShiftDR.U
  drShifter.io.chainIn.data := io.jtag.TDI
  drShifter.io.chainIn.capture := currState === JtagState.CaptureDR.U
  drShifter.io.chainIn.update := currState === JtagState.UpdateDR.U
  drShifter.io.capture.bits := "b01".U
  
  val updateData = Wire(Bool())
  updateData := DontCare
  val newData = Wire(UInt((beatBytes*8).W))
  newData := DontCare
  val currentData = NegativeEdgeLatch(clock, newData, updateData)
  
  when (reset.asBool) {
    newData := 0.U
    updateData := false.B
  } .elsewhen (currState === JtagState.UpdateDR.U) {
    newData := drShifter.io.update.bits
    updateData := true.B
  } .otherwise {
    updateData := false.B
  }
  
  io.dataOut := currentData

}


class JTAGToMasterTL[D, U, E, O, B <: Data](irLength: Int, initialInstruction: BigInt, beatBytes: Int)  extends LazyModule()(Parameters.empty) {
  
  val node = Some(TLClientNode(Seq(TLClientPortParameters(Seq(TLClientParameters(name="JTAGToMasterOut", sourceId = IdRange(0, 4)))))))

  lazy val io = Wire(new topModuleIO)

  

  lazy val module = new LazyModuleImp(this) {
  
    val (tl, edge) = node.get.out(0)
    
    val jtagClk = Wire(Clock())
    jtagClk := io.jtag.TCK.asClock
  
    val syncReset = Wire(Bool())
    val controller = withClockAndReset(jtagClk, syncReset) { Module(new JtagController(irLength, initialInstruction, beatBytes))}
    syncReset := controller.io.output.reset
    controller.io.jtag.TCK := io.jtag.TCK
    controller.io.jtag.TMS := io.jtag.TMS
    controller.io.jtag.TDI := io.jtag.TDI
    io.jtag.TDO := controller.io.jtag.TDO
    controller.io.control.fsmAsyncReset := io.asyncReset
    
    object State extends ChiselEnum {
      val sIdle, sSetDataA, sResetValidA, sSetReadyD, sReceiveDataD = Value
    }
    val state = RegInit(State.sIdle)
    
    val currentInstruction = RegInit(UInt(irLength.W), initialInstruction.U)
    currentInstruction := controller.io.output.instruction
    
    val dataValue = RegInit(UInt((beatBytes * 8).W), 0.U)
    val addressValue = RegInit(UInt((beatBytes * 4).W), 0.U)
    
    when (currentInstruction === "b011".U) {
      dataValue := controller.io.dataOut
    }
    
    when (currentInstruction === "b010".U) {
      addressValue := controller.io.dataOut >> (beatBytes * 4)
    }
    
    val shouldWrite = RegInit(Bool(), false.B)
    when ((currentInstruction === "b001".U) && (RegNext(currentInstruction)=/= "b001".U)) {
      shouldWrite := true.B
    } .elsewhen(state === State.sSetDataA) {
      shouldWrite := false.B
    }
    
    
    switch (state) {
      is (State.sIdle) {
        when (shouldWrite) {
          state := State.sSetDataA
        }
        
        tl.d.ready := false.B
        tl.a.valid := false.B
        
        tl.a.bits.opcode := 0.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := 2.U
        tl.a.bits.source := 1.U
        tl.a.bits.address := 0.U
        tl.a.bits.mask := 255.U
        tl.a.bits.data := 0.U
      }
      is (State.sSetDataA) {
        when (tl.a.ready) {
          state := State.sResetValidA
        }
        
        tl.d.ready := false.B
        tl.a.valid := true.B
        
        tl.a.bits.opcode := 0.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := 2.U
        tl.a.bits.source := 1.U
        tl.a.bits.address := addressValue
        tl.a.bits.mask := 255.U
        tl.a.bits.data := dataValue
      }
      is (State.sResetValidA) {
        state := State.sSetReadyD
        
        tl.d.ready := false.B
        tl.a.valid := false.B
        
        tl.a.bits.opcode := 0.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := 2.U
        tl.a.bits.source := 1.U
        tl.a.bits.address := 0.U
        tl.a.bits.mask := 255.U
        tl.a.bits.data := 0.U
      }
      is (State.sSetReadyD) {
        when (tl.d.valid) {
          state := State.sReceiveDataD
        }
        
        tl.d.ready := true.B
        tl.a.valid := false.B
        
        tl.a.bits.opcode := 0.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := 2.U
        tl.a.bits.source := 1.U
        tl.a.bits.address := 0.U
        tl.a.bits.mask := 255.U
        tl.a.bits.data := 0.U
      }
      is (State.sReceiveDataD) {
        state := State.sIdle
        
        tl.d.ready := false.B
        tl.a.valid := false.B
        
        tl.a.bits.opcode := 0.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := 2.U
        tl.a.bits.source := 1.U
        tl.a.bits.address := 0.U
        tl.a.bits.mask := 255.U
        tl.a.bits.data := 0.U
      }
    }

  }
}


class TLJTAGToMasterBlock(irLength: Int = 3, initialInstruction: BigInt = BigInt("0", 2), beatBytes: Int = 4, addresses: AddressSet)(implicit p: Parameters) extends JTAGToMasterTL[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle](irLength, initialInstruction, beatBytes) {
  val devname = "TLJTAGToMasterBlock"
  val devcompat = Seq("jtagToMaster", "radardsp")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  
  def makeIO2(): topModuleIO = {
    val io2: topModuleIO = IO(io.cloneType)
    io2.suggestName("ioJTAG")
    io2 <> io
    io2
  }
  
  val managerParams = TLManagerParameters(
    address = Seq(addresses),
    resources = device.reg,
    regionType = RegionType.UNCACHED,
    executable = true,
    supportsArithmetic = TransferSizes(1, beatBytes),
    supportsLogical = TransferSizes(1, beatBytes),
    supportsGet = TransferSizes(1, beatBytes),
    supportsPutFull = TransferSizes(1, beatBytes),
    supportsPutPartial = TransferSizes(1, beatBytes),
    supportsHint = TransferSizes(1, beatBytes),
    fifoId = Some(0))
  
  val ioStreamNode = BundleBridgeSink[TLBundle]()
  ioStreamNode := TLToBundleBridge(managerParams, beatBytes) := node.get
  val ioTL = InModuleBody { ioStreamNode.makeIO() }
  val ioJTAG = InModuleBody { makeIO2() }
  
}


class JTAGToMasterAXI4(irLength: Int, initialInstruction: BigInt, beatBytes: Int, address: AddressSet)  extends LazyModule()(Parameters.empty) {
  
  val node = Some(AXI4MasterNode(Seq(AXI4MasterPortParameters(Seq(AXI4MasterParameters("ioAXI4"))))))

  lazy val io = Wire(new topModuleIO)

  lazy val module = new LazyModuleImp(this) {
  
    val (ioNode, _) = node.get.out(0)
    
    val jtagClk = Wire(Clock())
    jtagClk := io.jtag.TCK.asClock
  
    val syncReset = Wire(Bool())
    val controller = withClockAndReset(jtagClk, syncReset) { Module(new JtagController(irLength, initialInstruction, beatBytes))}
    syncReset := controller.io.output.reset
    controller.io.jtag.TCK := io.jtag.TCK
    controller.io.jtag.TMS := io.jtag.TMS
    controller.io.jtag.TDI := io.jtag.TDI
    io.jtag.TDO := controller.io.jtag.TDO
    controller.io.control.fsmAsyncReset := io.asyncReset
    
    object State extends ChiselEnum {
      val sIdle, sSetDataAndAddress, sResetCounterW, sSetReadyB = Value
    }
    val state = RegInit(State.sIdle)
    
    val currentInstruction = RegInit(UInt(irLength.W), initialInstruction.U)
    currentInstruction := controller.io.output.instruction
    
    val dataValue = RegInit(UInt((beatBytes * 8).W), 0.U)
    val addressValue = RegInit(UInt((beatBytes * 4).W), 0.U)
    
    when (currentInstruction === "b011".U) {
      dataValue := controller.io.dataOut
    }
    
    when (currentInstruction === "b010".U) {
      addressValue := controller.io.dataOut >> (beatBytes * 4)
    }
    
    val shouldWrite = RegInit(Bool(), false.B)
    when ((currentInstruction === "b001".U) && (RegNext(currentInstruction)=/= "b001".U)) {
      shouldWrite := true.B
    } .elsewhen(state === State.sSetDataAndAddress) {
      shouldWrite := false.B
    }
    
    val dataSize = if (beatBytes == 4) 2 else 3
    
    def maxWait = 500
    val counter = RegInit(UInt(9.W), 0.U)
    
    switch (state) {
      is (State.sIdle) {
        when (shouldWrite) {
          state := State.sSetDataAndAddress
        }
        ioNode.aw.valid := false.B
        ioNode.w.valid := false.B
        ioNode.b.ready := false.B
        
        ioNode.aw.bits.addr := 0.U
        ioNode.aw.bits.size := dataSize.U
        ioNode.w.bits.data := 0.U
        ioNode.w.bits.last := false.B
        ioNode.w.bits.strb := 255.U
        
        counter := 0.U
      }
      is (State.sSetDataAndAddress) {
        when (ioNode.w.ready && ioNode.aw.ready) {
          state := State.sResetCounterW
        } .elsewhen(counter >= maxWait.U) {
          state := State.sIdle
        }
        ioNode.aw.valid := true.B
        ioNode.w.valid := true.B
        ioNode.b.ready := false.B
        
        ioNode.aw.bits.addr := addressValue
        ioNode.aw.bits.size := dataSize.U
        ioNode.w.bits.data := dataValue
        ioNode.w.bits.last := true.B
        ioNode.w.bits.strb := 255.U
        
        //require(counter < maxWait.U, s"Timeout waiting for AW or W to be ready ($maxWait cycles)")
        counter := counter + 1.U
      }
      is (State.sResetCounterW) {
        state := State.sSetReadyB
        
        ioNode.aw.valid := false.B
        ioNode.w.valid := false.B
        ioNode.b.ready := false.B
        
        counter := 0.U
      }
      is (State.sSetReadyB) {
        when (ioNode.b.valid && (ioNode.b.bits.resp === 0.U) && (ioNode.b.bits.id === ioNode.aw.bits.id)) {
          state := State.sIdle
        } .elsewhen(counter >= maxWait.U) {
          state := State.sIdle
        }
        ioNode.aw.valid := false.B
        ioNode.w.valid := false.B
        ioNode.b.ready := true.B
        
        ioNode.aw.bits.addr := 0.U
        ioNode.aw.bits.size := dataSize.U
        ioNode.w.bits.data := 0.U
        ioNode.w.bits.last := false.B
        ioNode.w.bits.strb := 255.U
        
        //require(counter < maxWait.U, s"Timeout waiting for AW or W to be ready ($maxWait cycles)")
        counter := counter + 1.U
      }
    }

  }
}


class AXI4JTAGToMasterBlock(irLength: Int = 3, initialInstruction: BigInt = BigInt("0", 2), beatBytes: Int = 4, addresses: AddressSet)(implicit p: Parameters) extends JTAGToMasterAXI4(irLength, initialInstruction, beatBytes, addresses) {
  
  def makeIO2(): topModuleIO = {
    val io2: topModuleIO = IO(io.cloneType)
    io2.suggestName("ioJTAG")
    io2 <> io
    io2
  }

  val slaveParams = AXI4SlaveParameters(
    address = Seq(addresses),
    regionType = RegionType.UNCACHED,
    executable = true,
    supportsWrite = TransferSizes(1, beatBytes),
    supportsRead = TransferSizes(1, beatBytes))
  
  val ioNode = BundleBridgeSink[AXI4Bundle]()
  ioNode := 
  AXI4ToBundleBridge(AXI4SlavePortParameters(Seq(slaveParams), beatBytes)) := node.get
  val ioAXI4 = InModuleBody { ioNode.makeIO() }
  
  val ioJTAG = InModuleBody { makeIO2() }
  
}



object JTAGToMasterDspBlockTL extends App
{
  implicit val p: Parameters = Parameters.empty
  val jtagModule = LazyModule(new TLJTAGToMasterBlock(3, BigInt("0", 2), 4, AddressSet(0x00000, 0x3FFF)))
  
  chisel3.Driver.execute(args, ()=> jtagModule.module)
}

object JTAGToMasterDspBlockAXI4 extends App
{
  implicit val p: Parameters = Parameters.empty
  val jtagModule = LazyModule(new AXI4JTAGToMasterBlock(3, BigInt("0", 2), 4, AddressSet(0x00000, 0x3FFF)))
  
  chisel3.Driver.execute(args, ()=> jtagModule.module)
}

