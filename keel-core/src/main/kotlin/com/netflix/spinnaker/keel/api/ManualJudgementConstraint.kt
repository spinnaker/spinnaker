package com.netflix.spinnaker.keel.api

import java.time.Duration

data class ManualJudgementConstraint(
  val timeout: Duration = Duration.ofDays(7)
) : StatefulConstraint("manual-judgement")
