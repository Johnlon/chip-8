package chip8

import java.beans.BeanProperty
import java.io.{File, FileInputStream}
import java.util
import java.util.Objects

import chip8.Instructions.decode
import chip8.KeypressAdaptor.{pressedKeys, registerKeypress}
import javax.sound.sampled.{AudioSystem, Clip}
import org.yaml.snakeyaml.constructor.Constructor

import scala.jdk.CollectionConverters.MapHasAsScala
import scala.swing.event.Key
import scala.swing.{Dimension, SwingApplication}

object Chip8Emulator extends SwingApplication {

  private val terminalComponent = new C8Terminal(receiveKey = registerKeypress)
  var oldScreen = Seq.empty[Seq[Boolean]]
  private var beep: Clip = null

  override def startup(args: Array[String]): Unit = {
    val romFileName = args(0)
    val romFile: File = Loader.resolveRom(romFileName)
    val bytes: List[U8] = Loader.read(romFile)

    val props = loadProps(romFile)
    KeypressAdaptor.keysMappings = props.keyMappings
    loadBeep(props)

    if (terminalComponent.size == new Dimension(0, 0))
      terminalComponent.pack()

    terminalComponent.title = romFileName
    terminalComponent.visible = true

    println("show keys")

    val emulatorThread = new Thread(new Runnable() {
      override def run(): Unit = {
        while (true) {
          // display help page if defined
          if (!props.keys.isEmpty) {
            terminalComponent.publish(DisplayKeysEvent(props))
            Thread.sleep(3000)
          }

          // run
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

      message("Init rom ...")

      var state = State()

      message("Init fonts ...")
      state = Fonts.installFonts(state)

      println("Loading program...")
      program.zipWithIndex.foreach {
        case (byte, z) =>

          val address = INITIAL_PC.toInt + z

          val newMemory = state.memory.set(address, byte)
          state = state.copy(memory = newMemory)
      }

      message("Run ...")

      var stepMode = true
      if (stepMode) {
        message("Step Mode - hit escape to continue")
      }

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

  private def message(s: String): Unit = {
    terminalComponent.publish(MessageEvent(s))
  }

  private def soundStatus(play: Boolean): Unit = {
    if (play) {
      if (!beep.isRunning) {
        beep.setFramePosition(0)
        beep.start()
      }
    } else {
      //beep.stop()
    }
  }

  private def drawScreen(state: State): Unit = {
    val pixels: Seq[Boolean] = state.screenBuffer.flatMap(x => intTo8Bits(x.ubyte).reverse)
    val data: Seq[Seq[Boolean]] = pixels.grouped(C8_SCREEN_WIDTH).toSeq
    if (data != oldScreen) terminalComponent.publish(DrawScreenEvent(data))
    oldScreen = data
  }

  private def debugHandler(stepModeIn: Boolean): Boolean = {

    import KeypressAdaptor._

    var stepMode = stepModeIn
    if (pressedKeys.contains(Key.Escape.toString)) {
      stepMode = !stepMode
      while (pressedKeys.contains(Key.Escape.toString)) {
        // wait for release
      }
    }
    if (stepMode) {
      System.out.println("Waiting in step mode - hit enter or escape")

      while (!isPressed(Key.Enter) && !isPressed(Key.Escape)) {
        // wait for key
      }
      if (isPressed(Key.Escape)) {
        stepMode = !stepMode
        while (isPressed(Key.Escape)) {
          // wait for release
        }
      }
      if (isPressed(Key.Enter)) {
        while (isPressed(Key.Enter)) {
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

  private def loadProps(romFile: File): ExtraProps = {
    val propsFile = new File(romFile.getAbsolutePath + ".yaml")
    if (propsFile.exists()) {

      import org.yaml.snakeyaml.Yaml

      val yaml = new Yaml(new Constructor(classOf[ExtraProps]))
      val inputStream = new FileInputStream(propsFile)
      val obj = yaml.load(inputStream).asInstanceOf[ExtraProps]
      obj
    } else
      new ExtraProps()
  }

  private def loadBeep(props: ExtraProps): Unit = {
    if (props.introMusic != null) {
      loadMusic(props.introMusic)
    }
    val beepFile = props.beepFile
    val clip: Clip = loadMusic(beepFile)
    beep = clip
  }

  private def loadMusic(beepFile: String) = {
    val sound = this.getClass.getResourceAsStream(beepFile)
    Objects.requireNonNull(sound, "failed to load sound file : " + beepFile)
    val audioInputStream = AudioSystem.getAudioInputStream(sound)
    val clip = AudioSystem.getClip
    clip.open(audioInputStream)

    clip.start() // necessary to run once to avoid lag in game first time played
    clip.setFramePosition(0)
    clip
  }
}

class Key {
  @BeanProperty var desc: String = null
  @BeanProperty var alias: String = null
}

class ExtraProps {
  @BeanProperty var keys: java.util.Map[String, Key] = new util.HashMap[String, Key]()
  @BeanProperty var beepFile: String = "./ping_pong_8bit_beeep.aiff"
  @BeanProperty var introMusic: String = null

  def keyMappings: Seq[(String, String)] = {
    if (keys == null) return Seq.empty
    keys.asScala.map {
      case (k, v) =>
        Key.withName(v.alias) // just to verify name
        Key.withName(k) // just to verify name
        (v.alias, k)
    }.toSeq

  }
}
