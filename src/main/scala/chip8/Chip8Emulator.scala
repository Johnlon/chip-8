package chip8

import java.io.File

import chip8.Instructions.decode
import javax.sound.sampled.AudioSystem

import scala.swing.event.Key
import scala.swing.{Dimension, SwingApplication}

object Chip8Emulator extends SwingApplication {

  private val terminalComponent = new C8Terminal(receiveKey = KeypressAdaptor.registerKeypress)

  override def startup(args: Array[String]): Unit = {
    val romFile = args(0)
    val romData: File = Loader.resolveRom(romFile)
    val bytes: List[U8] = Loader.read(romData)

    if (terminalComponent.size == new Dimension(0, 0))
      terminalComponent.pack()

    terminalComponent.visible = true

    val emulatorThread = new Thread(new Runnable() {
      override def run(): Unit = {
        while (true) {
          startEmulation(bytes)
          System.exit(1)
          System.out.println("exit!")
        }
      }
    })

    emulatorThread.start()
  }

  private def startEmulation(program: List[U8]): Unit = {

    try {
      if (program.isEmpty) sys.error("program is empty")

      println("Init rom ...")

      var state = State()

      println("Init fonts ...")
      state = Fonts.installFonts(state)

      println("Loading program...")
      program.zipWithIndex.foreach {
        case (byte, z) =>

          val address = INITIAL_PC.toInt + z

          val newMemory = state.memory.set(address, byte)
          state = state.copy(memory = newMemory)
      }

      println("Run ...")
      var stepMode = false

      var lastCountDownTime = System.nanoTime()
      val countDownIntervalNs60Hz = (1000 * 1000000) / 60
      var lastStepTime = System.nanoTime()
      val stepIntervalNs = (1000 * 1000000) / 500
      while (true) {
        // busy wait to eat up remaining time slice - busy to get more accurate timings
        var now = System.nanoTime()
        var remainingNs = stepIntervalNs - (now - lastStepTime)
        while (remainingNs > 0) {
          now = System.nanoTime()
          remainingNs = stepIntervalNs - (now - lastStepTime)
        }
        lastStepTime = now

        // process instructions
        val inst: Instruction = decode(state)
        terminalComponent.publish(UpdateInstructionStatsEvent(inst))

        stepMode = debugHandler(stepMode)

        // load keyboard state
        state = state.copy(
          pressedKeys = KeypressAdaptor.pressedKeys
        )

        // do exec
        state = inst.exec(state)

        terminalComponent.publish(UpdateStateEvent(state))

        soundStatus(state.soundTimer.ubyte > 0)

        // only update counters at 60hz
        now = System.nanoTime()
        remainingNs = countDownIntervalNs60Hz - (now - lastCountDownTime)
        if (remainingNs <= 0) {

          drawScreen(state)

          state = state.copy(
            delayTimer = decrementDelay(state),
            soundTimer = decrementSound(state),
          )
          lastCountDownTime = now
        }

      }
    } catch {
      case ex: Throwable =>
        terminalComponent.displayError(ex)
        ex.printStackTrace(System.err)
        System.in.read()
        this.shutdown()
    }
  }

  private val beep = {
    val sound = this.getClass.getResourceAsStream("./ping_pong_8bit_beeep.aiff")
    val audioInputStream = AudioSystem.getAudioInputStream(sound)
    val clip = AudioSystem.getClip
    clip.open(audioInputStream)
    clip
  }

  private def soundStatus(play: Boolean): Unit = {
    if (play) {
      if (!beep.isRunning) {
        beep.setFramePosition(0)
        beep.start()
      }
    } else {
      beep.stop()
    }
  }

  private def drawScreen(state: State): Unit = {
    val pixels: Seq[Boolean] = state.screenBuffer.flatMap(x => intTo8Bits(x.ubyte).reverse)
    val data: Seq[Seq[Boolean]] = pixels.grouped(SCREEN_WIDTH).toSeq
    terminalComponent.publish(DrawScreenEvent(data))
  }

  private def debugHandler(stepModeIn: Boolean): Boolean = {
    var stepMode = stepModeIn
    if (KeypressAdaptor.pressedKeys.contains(Key.Escape)) {
      stepMode = !stepMode
      while (KeypressAdaptor.pressedKeys.contains(Key.Escape)) {
        // wait for release
      }
    }
    if (stepMode) {
      while (!KeypressAdaptor.pressedKeys.contains(Key.Enter) && !KeypressAdaptor.pressedKeys.contains(Key.Escape)) {
        // wait for key
      }
      if (KeypressAdaptor.pressedKeys.contains(Key.Escape)) {
        stepMode = !stepMode
        while (KeypressAdaptor.pressedKeys.contains(Key.Escape)) {
          // wait for release
        }
      }
      if (KeypressAdaptor.pressedKeys.contains(Key.Enter)) {
        while (KeypressAdaptor.pressedKeys.contains(Key.Enter)) {
          // wait for release
        }
      }
    }
    stepMode
  }

  private def decrementDelay(state: State): U8 = {
    if (state.delayTimer > U8(0)) {
      state.delayTimer - 1
    } else
      U8(0)
  }

  private def decrementSound(state: State): U8 = {
    if (state.soundTimer > U8(0)) {
      state.soundTimer - 1
    } else
      U8(0)
  }
}

