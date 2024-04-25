// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { module } from 'angular';

import { Registry, SETTINGS } from '@spinnaker/core';

import { lambdaRouteStage } from './LambdaRouteStage';

export * from './LambdaRouteStage';

export const AMAZON_PIPELINE_STAGES_LAMBDA_ROUTE = 'spinnaker.amazon.pipeline.stage.Aws.LambdaTrafficRoutingStage';

module(AMAZON_PIPELINE_STAGES_LAMBDA_ROUTE, []).config(function () {
  if (SETTINGS.feature.lambdaAdditionalStages) {
    Registry.pipeline.registerStage(lambdaRouteStage);
  }
});
