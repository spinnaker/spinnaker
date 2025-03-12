package com.netflix.spinnaker.keel.core.api

import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction

/**
 * Configuration for specifying a tag ami post deploy action.
 * Applies to all images in the environment.
 *
 * Will apply specific tags to all images running.
 */
class TagAmiPostDeployAction : PostDeployAction() {
  override val type = "tag-ami"

  override val id: String
    get() = type
}
