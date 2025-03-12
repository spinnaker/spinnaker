package com.netflix.spinnaker.keel.api.postdeploy

import com.netflix.spinnaker.keel.api.action.Action
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.action.ActionType.POST_DEPLOY

abstract class PostDeployAction : Action {
  override val actionType: ActionType
    get() = POST_DEPLOY
}
