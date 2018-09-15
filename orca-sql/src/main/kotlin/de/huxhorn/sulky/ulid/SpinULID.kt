package de.huxhorn.sulky.ulid

import java.util.*

class SpinULID(random: Random): ULID(random) {
  val _random: Random? = random

  fun nextULID(timestamp: Long): String {
    return internalUIDString(timestamp, _random);
  }
}
