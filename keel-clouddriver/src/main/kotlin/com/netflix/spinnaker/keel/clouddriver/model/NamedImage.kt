package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.artifacts.DEBIAN_VERSION_COMPARATOR
import java.time.Instant
import java.time.Period

data class NamedImage(
  val imageName: String,
  val attributes: Map<String, Any?>,
  val tagsByImageId: Map<String, Map<String, String?>?>,
  val accounts: Set<String>,
  val amis: Map<String, List<String>?>,
  // image names come with an underscore in place of a tilde in the version, which breaks bakery metadata lookups
  // (e.g. lpollo-local-test-0.0.1_snapshot instead of lpollo-local-test-0.0.1~snapshot)
  val normalizedImageName: String = imageName.replaceFirst('_', '~')
) {
  constructor(imageName: String) : this(imageName, emptyMap(), emptyMap(), emptySet(), emptyMap())
}

object NamedImageComparator : Comparator<NamedImage> {
  override fun compare(a: NamedImage, b: NamedImage): Int {
    val byAppVersion = DEBIAN_VERSION_COMPARATOR.compare(a.appVersion, b.appVersion)
    return if (byAppVersion == 0) {
      b.creationDate.compareTo(a.creationDate)
    } else {
      return byAppVersion
    }
  }
}


val NamedImage.creationDate: Instant
  get() =
    if (attributes["creationDate"] !is String) {
      // if no creation date, we will assume it is very old.
      // falling back to 3 years prior if creationDate is nil to support legacy resources
      Instant.now().minus(Period.ofYears(3))
    } else {
      attributes["creationDate"].toString().let(Instant::parse)
    }

val NamedImage.hasAppVersion: Boolean
  get() = tagsByImageId
    .values
    .let { vals ->
      vals.isNotEmpty() && vals.all { it != null && it.containsKey("appversion") }
    }

val NamedImage.hasBaseImageName: Boolean
  get() = tagsByImageId
    .values
    .let { vals ->
      vals.isNotEmpty() && vals.all { it != null && it.containsKey("base_ami_name") }
    }

val NamedImage.appVersion: String
  get() = tagsByImageId
    .values
    .first()
    ?.getValue("appversion")
    .toString()
    .substringBefore("/")

val NamedImage.baseImageName: String
  get() = tagsByImageId
    .values
    .first()
    ?.getValue("base_ami_name")
    .toString()
