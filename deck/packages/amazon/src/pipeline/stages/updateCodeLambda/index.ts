// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { module } from 'angular';

import { Registry, SETTINGS } from '@spinnaker/core';

import { lambdaUpdateCodeStage } from './LambdaUpdateCodeStage';

export * from './LambdaUpdateCodeStage';

export const AMAZON_PIPELINE_STAGES_LAMBDA_UPDATE = 'spinnaker.amazon.pipeline.stage.Aws.LambdaUpdateCodeStage';

module(AMAZON_PIPELINE_STAGES_LAMBDA_UPDATE, []).config(function () {
  if (SETTINGS.feature.lambdaAdditionalStages) {
    Registry.pipeline.registerStage(lambdaUpdateCodeStage);
  }
});
