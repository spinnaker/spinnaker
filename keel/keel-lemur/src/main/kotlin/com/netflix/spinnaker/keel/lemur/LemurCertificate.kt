package com.netflix.spinnaker.keel.lemur

import java.time.Instant

data class LemurCertificateResponse(
  val items: List<LemurCertificate>
)

data class LemurCertificate(
  val commonName: String,
  val name: String,
  val active: Boolean,
  val validityStart: Instant,
  val validityEnd: Instant,
  val replacedBy: List<LemurCertificate> = emptyList(),
  val replaces: List<LemurCertificate> = emptyList()
)
