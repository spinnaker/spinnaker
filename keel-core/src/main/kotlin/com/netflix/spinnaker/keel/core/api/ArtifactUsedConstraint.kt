package com.netflix.spinnaker.keel.core.api

import com.netflix.spinnaker.keel.api.Constraint

/**
 * A constraint that ensures only artifacts used in an envirionment are approved
 * for deployment into an environment.
 */
class ArtifactUsedConstraint : Constraint("artifact-used")
