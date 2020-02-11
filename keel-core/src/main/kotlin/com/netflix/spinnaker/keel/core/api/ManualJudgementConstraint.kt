package com.netflix.spinnaker.keel.core.api

import com.netflix.spinnaker.keel.api.StatefulConstraint
import java.time.Duration

data class ManualJudgementConstraint(
  val timeout: Duration = Duration.ofDays(7)
) : StatefulConstraint("manual-judgement")
