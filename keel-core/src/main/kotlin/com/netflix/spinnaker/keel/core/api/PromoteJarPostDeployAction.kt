package com.netflix.spinnaker.keel.core.api

import com.netflix.spinnaker.keel.api.postdeploy.PostDeployAction

/**
 * Configuration for specifying a promote candidate jar post deploy action.
 * Applies to all artifacts in the environment.
 */
class PromoteJarPostDeployAction: PostDeployAction("promote-candidate-jar")
