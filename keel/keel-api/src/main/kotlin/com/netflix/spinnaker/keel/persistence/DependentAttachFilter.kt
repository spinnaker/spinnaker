package com.netflix.spinnaker.keel.persistence

enum class DependentAttachFilter {
  ATTACH_NONE, ATTACH_ALL, ATTACH_ARTIFACTS, ATTACH_ENVIRONMENTS, ATTACH_PREVIEW_ENVIRONMENTS
}