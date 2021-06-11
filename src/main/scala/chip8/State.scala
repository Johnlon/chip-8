package chip8

import scala.swing.event.Event

object State {
  def NullListener(event: Event): Unit = {}
}

case class State(
                  pc: U12 = INITIAL_PC,
                  index: U12 = U12(0),
                  stack: Seq[U12] = Nil,
                  register: Seq[U8] = emptyRegisters,
                  memory: Seq[U8] = emptyMemory,
                  delayTimer: U8 = U8(0),
                  soundTimer: U8 = U8(0),
                  fontCharLocation: Int => U12 = Fonts.fontCharLocation,
                  pressedKeys: Set[String] = Set(),
                ) {

  if (stack.length > 16) {
    sys.error("Stack may not exceed 16 levels but got " + stack.length)
  }

  def currentInstruction: Instruction = {
    Instructions.decode(this)
  }

  def clearScreen(): State = {
    var st = this
    (0 until C8_SCREEN_HEIGHT) foreach { y =>
      (0 until C8_SCREEN_WIDTH) foreach { x =>
        st = st.writePixel(x, y, flip = false)
      }
    }
    st
  }

  // if flip = False then bit is cleared, otherwise bit is flipped
  def writePixel(x: Int, y: Int, flip: Boolean): State = {
    //x=0,y=0 is at top left of screen and is lowest point in memory
    val xMod = x % C8_SCREEN_WIDTH
    val yMod = y % C8_SCREEN_HEIGHT

    val offset = (yMod * C8_SCREEN_WIDTH) + xMod
    val byteNum = offset / 8
    val bitNum = offset % 8

    val memoryLocation = SCREEN_BUF_BOT + byteNum
    if (memoryLocation > SCREEN_BUF_TOP) {
      sys.error(s"memory error : $memoryLocation > $SCREEN_BUF_TOP")
    }

    val bitMask: Int = 1 << bitNum
    val existingByte = memory(memoryLocation)
    val existingBitSet = (existingByte.toInt & bitMask) != 0
    val signalOverwrite = flip && existingBitSet

    val newByte = if (flip) {
      val newBit = !existingBitSet
      if (newBit)
        existingByte | bitMask // set the bit
      else
        existingByte & (~bitMask) // clear the bit
    } else {
      existingByte & (~bitMask) // clear the bit
    }

    val newReg = if (signalOverwrite)
      register.set(STATUS_REGISTER_ID, U8(1))
    else
      register

    copy(memory = memory.set(memoryLocation, newByte), register = newReg)
  }

  def screenBuffer: Seq[U8] = {
    memory.slice(SCREEN_BUF_BOT, SCREEN_BUF_TOP + 1)
  }

  def push(i: U12): State = copy(stack = i +: stack)

  def pop: (State, U12) = {
    if (stack.isEmpty)
      sys.error("attempt to pop empty stack ")

    val popped :: tail = stack
    (copy(stack = tail), popped)
  }
}
