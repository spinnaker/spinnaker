package com.netflix.spinnaker.keel.persistence

import java.time.Instant

sealed class AssetState(
) {
  abstract val at: Instant

  data class Unknown(override val at: Instant) : AssetState()
  data class Ok(override val at: Instant) : AssetState()
  data class Diff(override val at: Instant) : AssetState()
  data class Missing(override val at: Instant) : AssetState()
}
