package com.netflix.spinnaker.keel.persistence

enum class PromotionStatus {
  PENDING, APPROVED, DEPLOYING, CURRENT, PREVIOUS
}
