package com.netflix.spinnaker.keel.model

import com.netflix.spinnaker.keel.api.actuation.Job

class OrcaJob(type: String, m: Map<String, Any?>) :
  HashMap<String, Any?>(m + mapOf("type" to type, "user" to "Spinnaker")), Job
