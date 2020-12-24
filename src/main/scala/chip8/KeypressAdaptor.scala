package chip8

import scala.swing.event.{Key, KeyEvent, KeyPressed, KeyReleased}

object KeypressAdaptor {

  @volatile
  private var keys = Set.empty[Key.Value]

  @volatile
  var keysMappings = Seq.empty[(Key.Value, Key.Value)]

  def pressedKeys: Set[Key.Value] = {
    keys
  }

  def registerKeypress(ke: KeyEvent): Unit = {
    ke match {
      case KeyPressed(_, k, _, _) =>
        val effK: Key.Value = keyAlternatives(k)
        keys = keys + effK
      case KeyReleased(_, k, _, _) =>
        val effK: Key.Value = keyAlternatives(k)
        keys = keys - effK
      case _ => // ignore
    }
  }

  // seek http://www.sunrise-ev.com/photos/1802/Chip8interpreter.pdf
  private def keyAlternatives(k: Key.Value): Key.Value = {
    // can't map by Key.Value type because this is a crap Scala enum that doesn't
    // guarantee identity of each value - there can be more than one instance of Key.Up in memory
    // only the name is reliably preserved
    val value = keysMappings.filter(_._1.toString == k.toString).map(_._2).headOption.getOrElse(k)
    value
  }
}

