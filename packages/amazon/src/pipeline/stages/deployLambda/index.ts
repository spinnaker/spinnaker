// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { module } from 'angular';

import { Registry, SETTINGS } from '@spinnaker/core';

import { lambdaDeploymentStage } from './config/LambdaDeploymentStage';

export * from './config/LambdaDeploymentStage';

export const AMAZON_PIPELINE_STAGES_LAMBDA_DEPLOY = 'spinnaker.amazon.pipeline.stage.Aws.LambdaDeploymentStage';

module(AMAZON_PIPELINE_STAGES_LAMBDA_DEPLOY, []).config(function () {
  if (SETTINGS.feature.lambdaAdditionalStages) {
    Registry.pipeline.registerStage(lambdaDeploymentStage);
  }
});
