// use SpamCC o write emulator !!
//package chip8
//
//class Spam1Chip8Compiler {
//
//  var labelIdx = 0
//
//  def newLabel(): String = {
//    s"label_$labelIdx"
//  }
//
//  val consts = Seq(
//    s"""
//       |uint8 STATE_NONE=0;
//       |uint8 DO_CLEAR=0;
//       |""")
//:q!:Q!
//  def init: (Seq[String], Int) = {
//    val (putcharCodeSN, pcIncSN) = putchar(":STATE_NONE")
//    val (putcharCodeDN, pcIncDN) = putchar(":DO_NONE")
//
//    (split(
//      s"""
//         |$putcharCodeSN
//         |$putcharCodeDN
//         |"""), pcIncSN+pcIncDN)
//  }
//
//  def putchar(str: String): (Seq[String], Int) = {
//    val wait = newLabel()
//    val write = newLabel()
//    (split(
//      s"""
//         |$wait:
//         |PCHITMP = < :$write
//         |PC = > :$write _DO
//         |PCHITMP = < :$wait
//         |PC = > :$wait
//         |$write:
//         |UART = $str
//         |"""), 5)
//  }
//
//  def compile(pc: Int, inst: Instruction): (Seq[String], Int) = {
//    inst match {
//      case ClearScreen(op) =>
//        val (putcharCode, pcInc) = putchar(":DO_CLEAR")
//
//        (split(
//          s"""
//             |// $op : ${inst.toString}
//             |$putcharCode
//             |"""), pcInc)
//
//      case u => {
//        println("unknown " + u)
//        (Nil, pc)
//      }
//    }
//  }
//
//
//  def split(s: String): List[String] = {
//    s.split("\\|").map(_.stripTrailing().stripLeading()).filterNot(_.isEmpty).toList
//  }
//}
