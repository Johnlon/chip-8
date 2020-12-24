package chip8

import java.awt.Color

import chip8.C8Terminal._
import javax.swing.BorderFactory
import javax.swing.border.EmptyBorder

import scala.jdk.CollectionConverters.MapHasAsScala
import scala.swing.Dialog.{Message, Options}
import scala.swing.event._
import scala.swing.{Rectangle, _}

object C8Terminal {

  val FONT = "Courier New"

  def BLANK: PixelType = ' '

  def BLOCK: PixelType = 0x2588.toChar

  val NO_CHAR: Char = 0

  // control
  val WRITE: Char = 'd'
  val SETX: Char = 'x'
  val SETY: Char = 'y'

  // data
  val K_UP: Char = 'U'
  val K_DOWN: Char = 'D'
  val K_LEFT: Char = 'L'
  val K_RIGHT: Char = 'R'

}

class C8Terminal(receiveKey: KeyEvent => Unit) extends MainFrame with Publisher {
  term =>

  private val PaneWidth = 675
  private val PaneHeight = 400
  private val BotHeight = 300
  private val statWidth = PaneWidth * 2 / 3

  private var lastInstructionTime = System.currentTimeMillis()
  private var totalInstCount = 0
  private var instCount = 0
  private var instructionRate = 0L

  private var lastDraw: Long = System.currentTimeMillis()
  private var drawCount = 0
  private var drawRate = 0L

  bounds = new Rectangle(50, 50, PaneWidth, PaneHeight + BotHeight)

  private val gameScreen = {
    val gameScreen = new TextArea()
    gameScreen.border = BorderFactory.createLineBorder(Color.BLUE)
    gameScreen.preferredSize = new Dimension(PaneWidth, PaneHeight)
    gameScreen.font = new Font(FONT, scala.swing.Font.Plain.id, 10)
    gameScreen.editable = false
    gameScreen.background = Color.BLACK
    gameScreen.foreground = Color.WHITE
    gameScreen
  }

  private val stateScreen = {
    val stateScreen = new TextArea()
    stateScreen.border = BorderFactory.createLineBorder(Color.RED)
    stateScreen.font = new Font(FONT, scala.swing.Font.Plain.id, 10)
    stateScreen.preferredSize = new Dimension(statWidth, BotHeight)
    stateScreen.editable = false
    stateScreen.focusable = false
    stateScreen
  }

  private val instScreen = {
    val instScreen = new TextArea()
    instScreen.border = BorderFactory.createLineBorder(Color.GREEN)
    instScreen.font = new Font(FONT, scala.swing.Font.Plain.id, 10)
    instScreen.preferredSize = new Dimension(PaneWidth - statWidth, BotHeight)
    instScreen.editable = false
    instScreen.focusable = false
    instScreen
  }

  gameScreen.requestFocus()

  listenTo(gameScreen.keys, term)

  reactions += {

    case e@KeyPressed(_, _, _, _) =>
      receiveKey(e)

    case e@KeyReleased(_, _, _, _) =>
      receiveKey(e)

    case DrawScreenEvent(bits) =>
      gameScreen.requestFocus()
      drawCount += 1
      val now = System.currentTimeMillis()
      val elapsed = now - lastDraw
      drawRate = (1000 * drawCount) / (1 + elapsed)

      if (drawCount > 100) {
        drawCount = 0
        lastDraw = now
      }

      doRepaint(bits)

    case UpdateInstructionStatsEvent(inst) =>
      gameScreen.requestFocus()
      updateInstView(inst)

    case UpdateStateEvent(state) =>
      gameScreen.requestFocus()
      updateStatsView(state)

    case DisplayKeysEvent(keys) =>
      gameScreen.requestFocus()
      val t = keys.keys.asScala.map {
        case (baseKey, detail) =>
          f"""Key : $baseKey%-8s ${detail.desc}
             |      ${detail.alias}
             |""".stripMargin
      }.mkString("\n")
      gameScreen.text = t

    case MessageEvent(s) =>
      gameScreen.requestFocus()
      gameScreen.text = s
     // Thread.sleep(1000)
  }

  contents = new BoxPanel(Orientation.Vertical) {
    val left: BoxPanel = new BoxPanel(Orientation.Vertical) {
      self: Component =>
      self.border = new EmptyBorder(10, 10, 10, 10)
      contents ++= Seq(gameScreen)
    }
    val right: BoxPanel = new BoxPanel(Orientation.Horizontal) {
      contents ++= Seq(stateScreen, instScreen)
    }
    contents ++= Seq(left, right)
  }

  private def doRepaint(screen: Seq[Seq[Boolean]]): Unit = {
    val t = "\n" + screen.map { row =>
      row.map {
        cell =>
          val c = if (cell) BLOCK else BLANK
          s"$c$c"
      }.mkString("")
    }.mkString("\n")

    gameScreen.text = t
  }

  private var shownPcWarning = false
  private def updateStatsView(state: State): Unit = {

    if (state.pc.toInt % 2 != 0) {
      if (!shownPcWarning) {
        Dialog.showConfirmation(this, "odd pc " + state.pc + " : " + state.currentInstruction, optionType= Options.OkCancel, messageType = Message.Warning)
        shownPcWarning = true
      }
    }

    def printReg(regIdx: Seq[(chip8.U8, Int)]): String = {
      regIdx.map { case (z, i) =>
        val ubyte = z.ubyte.toInt
        f"V$i%1X=$ubyte%02x"
      }.mkString(" ")
    }

    val regIdx = state.register.zipWithIndex
    val registers1 = printReg(regIdx.take(8))
    val registers2 = printReg(regIdx.drop(8))
    val stack = state.stack.map { d => f"${d.toInt}%03x" }.mkString(" ")
    val index = state.index
    val soundTimer = state.soundTimer.ubyte.toInt
    val delayTimer = state.delayTimer.ubyte.toInt
    val pc = state.pc
    val keys = state.pressedKeys.map { k => f"$k%s" }.mkString(" ")

    stateScreen.text =
      f"""
         |instruction count : $totalInstCount%d
         |instruction rate  : $instructionRate%4d/s
         |frame rate        : $drawRate%4d/s
         |
         |pc                : ${pc.toInt}%03x
         |reg               : $registers1
         |                    $registers2
         |idx               : ${index.toInt}%03x
         |stack             : $stack
         |keys              : $keys
         |sound timer       : $soundTimer%-3d
         |delay timer       : $delayTimer%-3d
         |""".stripMargin
  }

  private def updateInstView(instruction: Instruction): Unit = {
    totalInstCount += 1
    instCount += 1
    val elapsed = System.currentTimeMillis() - lastInstructionTime
    instructionRate = (1000 * instCount) / (1 + elapsed)

    if (elapsed > 1000) {
      instCount = 0
      lastInstructionTime = System.currentTimeMillis()
    }

    //    val curText = instScreen.text
    //    val text = instruction.toString + curText.substring(0, Math.min(curText.length, 1000))
    //    instScreen.text = text
    instScreen.text = instruction.toString
  }

  def displayError(ex: Throwable): Unit = {
    gameScreen.text = ex.toString
  }
}
