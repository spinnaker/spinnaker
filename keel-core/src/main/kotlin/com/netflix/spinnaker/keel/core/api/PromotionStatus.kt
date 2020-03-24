package com.netflix.spinnaker.keel.core.api

enum class PromotionStatus {
  PENDING, APPROVED, DEPLOYING, CURRENT, PREVIOUS, VETOED
}
