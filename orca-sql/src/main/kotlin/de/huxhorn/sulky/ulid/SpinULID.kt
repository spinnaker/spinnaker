package de.huxhorn.sulky.ulid

import java.util.Random

class SpinULID(random: Random) : ULID(random) {
  val _random: Random? = random

  override fun nextULID(timestamp: Long): String {
    return internalUIDString(timestamp, _random)
  }
}
