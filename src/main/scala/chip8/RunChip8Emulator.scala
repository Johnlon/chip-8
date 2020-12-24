package chip8

import java.io.{File, InputStream}

import chip8.Instructions.decode
import javax.sound.sampled.{AudioSystem, Clip}

import scala.swing.event.Key
import scala.swing.{Frame, SimpleSwingApplication}

object RunChip8Emulator extends App {
  private val BLITZ = "BLITZ"
  private val UFO = "UFO"
  private val BLINKY = "BLINKY"
  private val TETRIS = "TETRIS"
  private val TANK = "TANK"
  private val PONG = "PONG"
  private val BRIX = "BRIX"
  private val BC_Test = "BC_Test"
  private val PONG2 = "PONG2"

  private val PUZZLE = "PUZZLE"
  private val TICTAC = "TICTAC"

  private val VERS = "VERS"
  private val WIPEOFF = "WIPEOFF"
  private val kaleid = "KALEID"
  private val testProgram = "corax89__test_opcode.ch8" // DOESNT PASS TES YET
  private val AIRPLANE = "Airplane.ch8"

  private val VBRIX = "VBRIX"
  private val ASTRO = "AstroDodge.ch8"
  private val SPACE_FLIGHT = "Space Flight.ch8"

  private val PARTICLE  = "Particle Demo [zeroZshadow, 2008].ch8"


  Chip8Emulator.main(Array("INVADERS"))
}

