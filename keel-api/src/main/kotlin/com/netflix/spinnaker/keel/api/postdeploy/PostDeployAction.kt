package com.netflix.spinnaker.keel.api.postdeploy

import com.netflix.spinnaker.keel.api.schema.Discriminator

abstract class PostDeployAction(@Discriminator val type: String)
