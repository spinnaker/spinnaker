package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.action.Action
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.action.ActionType.VERIFICATION

interface Verification : Action {
  override val actionType: ActionType
    get() = VERIFICATION
}
