package com.netflix.spinnaker.keel.scm

import com.netflix.spinnaker.keel.api.scm.CodeEvent
import com.netflix.spinnaker.keel.front50.model.Application

fun CodeEvent.matchesApplicationConfig(app: Application?): Boolean =
  app != null
    && repoType.equals(app.repoType, ignoreCase = true)
    && projectKey.equals(app.repoProjectKey, ignoreCase = true)
    && repoSlug.equals(app.repoSlug, ignoreCase = true)
