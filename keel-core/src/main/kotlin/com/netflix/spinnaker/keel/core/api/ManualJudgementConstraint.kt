package com.netflix.spinnaker.keel.core.api

import com.netflix.spinnaker.keel.api.StatefulConstraint
import java.time.Duration

/**
 * A manual judgement constraint.
 * Unless a timeout is set by the user, this constraint will never time out.
 */
data class ManualJudgementConstraint(
  val timeout: Duration? = null
) : StatefulConstraint("manual-judgement")
