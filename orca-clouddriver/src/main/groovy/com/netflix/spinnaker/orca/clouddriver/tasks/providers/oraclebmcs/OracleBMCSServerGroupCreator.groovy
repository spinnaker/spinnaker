/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.oraclebmcs

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class OracleBMCSServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {

  final boolean katoResultExpected = false
  final String cloudProvider = "oraclebmcs"

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    operation.putAll(stage.context)

    if (operation.account && !operation.credentials) {
      operation.credentials = operation.account
    }

    withImageFromPrecedingStage(stage, null, cloudProvider) {
      operation.imageId = operation.imageId ?: it.imageId
    }

    withImageFromDeploymentDetails(stage, null, cloudProvider) {
      operation.imageId = operation.imageId ?: it.imageId
    }

    if (!operation.imageId) {
      throw new IllegalStateException("No imageId could be found in ${stage.context.region}.")
    }

    return [[(ServerGroupCreator.OPERATION): operation]]
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.of("Oracle")
  }
}

