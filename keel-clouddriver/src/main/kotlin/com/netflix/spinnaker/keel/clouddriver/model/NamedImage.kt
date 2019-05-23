package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.clouddriver.Dates
import java.time.LocalDateTime
import java.time.ZoneId

data class NamedImage(
  val imageName: String,
  val attributes: Map<String, Any?>,
  val tagsByImageId: Map<String, Map<String, String?>?>,
  val accounts: Set<String>,
  val amis: Map<String, List<String>?>
)

class NamedImageComparator {

  companion object : Comparator<NamedImage> {

    override fun compare(a: NamedImage, b: NamedImage): Int =
      (a.creationMs - b.creationMs).toInt()
  }
}

private val NamedImage.creationMs: Long
  get() {
    if (!attributes.containsKey("creationDate") || attributes["creationDate"] !is String) {
      // if no creation date, we will assume it is very old.
      return LocalDateTime.now()
        .minusYears(3) // falling back to 3 years prior if creationDate is nil to support legacy resources
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    }
    val creationDate: String = this.attributes["creationDate"] as String
    return Dates.toLocalDateTime(creationDate)
      .atZone(ZoneId.systemDefault())
      .toInstant()
      .toEpochMilli()
  }
