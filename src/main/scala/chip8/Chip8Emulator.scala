package chip8

import java.beans.BeanProperty
import java.io.{File, FileInputStream}

import chip8.Instructions.decode
import chip8.KeypressAdaptor.{pressedKeys, registerKeypress}
import javax.sound.sampled.AudioSystem
import javax.xml.crypto.dsig.keyinfo.KeyValue
import org.yaml.snakeyaml.constructor.Constructor

import scala.jdk.CollectionConverters.MapHasAsScala
import scala.swing.event.Key
import scala.swing.{Dimension, SwingApplication}

object Chip8Emulator extends SwingApplication {

  private var beepFile: String = null

  private val terminalComponent = new C8Terminal(receiveKey = registerKeypress)

  override def startup(args: Array[String]): Unit = {
    val romFileName = args(0)
    val romFile: File = Loader.resolveRom(romFileName)
    val bytes: List[U8] = Loader.read(romFile)

    val maybeProps = loadProps(romFile)
    maybeProps.foreach { kp =>
      KeypressAdaptor.keysMappings = kp.keyMappings
    }

    if (terminalComponent.size == new Dimension(0, 0))
      terminalComponent.pack()

    terminalComponent.visible = true

    val emulatorThread = new Thread(new Runnable() {
      override def run(): Unit = {
        while (true) {
          startEmulation(bytes, maybeProps)
          System.exit(1)
          System.out.println("exit!")
        }
      }
    })

    emulatorThread.start()
  }

  private def startEmulation(program: List[U8], props: Option[ExtraProps]): Unit = {

    props.foreach {
      p =>
        beepFile = p.beepFile
    }

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
          pressedKeys = pressedKeys
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

  private def loadProps(romFile: File): Option[ExtraProps] = {
    val propsFile = new File(romFile.getAbsolutePath + ".yaml")
    if (romFile.exists()) {
      import org.yaml.snakeyaml.Yaml
      val yaml = new Yaml(new Constructor(classOf[ExtraProps]))
      val inputStream = new FileInputStream(propsFile)
      val obj = yaml.load(inputStream).asInstanceOf[ExtraProps]
      Some(obj)
    } else
      None
  }

  private lazy val beep = {
    val sound = this.getClass.getResourceAsStream(beepFile)
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
    import KeypressAdaptor._

    var stepMode = stepModeIn
    if (pressedKeys.contains(Key.Escape)) {
      stepMode = !stepMode
      while (pressedKeys.contains(Key.Escape)) {
        // wait for release
      }
    }
    if (stepMode) {
      while (!pressedKeys.contains(Key.Enter) && !pressedKeys.contains(Key.Escape)) {
        // wait for key
      }
      if (pressedKeys.contains(Key.Escape)) {
        stepMode = !stepMode
        while (pressedKeys.contains(Key.Escape)) {
          // wait for release
        }
      }
      if (pressedKeys.contains(Key.Enter)) {
        while (pressedKeys.contains(Key.Enter)) {
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

class ExtraProps {
  @BeanProperty var mappings: java.util.Map[String, String] = null
  @BeanProperty var beepFile: String = "./ping_pong_8bit_beeep.aiff"

  def keyMappings: Seq[(Key.Value, Key.Value)] = {
    if (mappings == null) return Seq.empty
    mappings.asScala.map {
      case (k, v) =>
        val from = Key.withName(k)
        val to = Key.withName(v)
        (from, to)
    }.toSeq
  }
}
