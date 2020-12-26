package chip8

import scala.swing.event.{Key, KeyEvent, KeyPressed, KeyReleased}

object KeypressAdaptor {

  @volatile
  var keysMappings = Seq.empty[(String, String)]
  @volatile
  private var keys = Set.empty[String]

  def pressedKeys: Set[String] = {
    keys
  }

  def isPressed(k: Key.Value): Boolean = {
    keys.contains(k.toString)
  }

  def registerKeypress(ke: KeyEvent): Unit = {
    ke match {
      case KeyPressed(_, k, _, _) =>
        val effK = keyAlternatives(k)
        keys = keys + effK
      case KeyReleased(_, k, _, _) =>
        val effK = keyAlternatives(k)
        keys = keys - effK
      case _ => // ignore
    }
  }

  // seek http://www.sunrise-ev.com/photos/1802/Chip8interpreter.pdf
  private def keyAlternatives(k: Key.Value): String = {
    val keyName = k.toString
    // can't map by Key.Value type because this is a crap Scala enum that doesn't
    // guarantee identity of each value - there can be more than one instance of Key.Up in memory
    // only the name is reliably preserved
    val value = keysMappings.filter(_._1 == keyName).map(_._2).headOption.getOrElse(keyName)
    value
  }
}

