// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { module } from 'angular';

import { Registry, SETTINGS } from '@spinnaker/core';

import { lambdaInvokeStage } from './LambdaInvokeStage';

export * from './LambdaInvokeStage';

export const AMAZON_PIPELINE_STAGES_LAMBDA_INVOKE = 'spinnaker.amazon.pipeline.stage.Aws.LambdaInvokeStage';

module(AMAZON_PIPELINE_STAGES_LAMBDA_INVOKE, []).config(function () {
  if (SETTINGS.feature.lambdaAdditionalStages) {
    Registry.pipeline.registerStage(lambdaInvokeStage);
  }
});
