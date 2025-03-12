// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { module } from 'angular';

import { Registry, SETTINGS } from '@spinnaker/core';

import { lambdaDeleteStage } from './LambdaDeleteStage';
export * from './LambdaDeleteStage';

export const AMAZON_PIPELINE_STAGES_LAMBDA_DELETE = 'spinnaker.amazon.pipeline.stage.Aws.LambdaDeleteStage';

module(AMAZON_PIPELINE_STAGES_LAMBDA_DELETE, []).config(function () {
  if (SETTINGS.feature.lambdaAdditionalStages) {
    Registry.pipeline.registerStage(lambdaDeleteStage);
  }
});
