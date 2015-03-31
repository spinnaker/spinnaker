package com.netflix.spinnaker.igor.history.model

import com.netflix.spinnaker.igor.jenkins.client.model.Project

/**
 * Encapsulates a build content block
 */
class BuildContent {
    Project project
    String master
}
