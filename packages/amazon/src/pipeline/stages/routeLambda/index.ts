// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { Registry, SETTINGS } from '@spinnaker/core';
import { lambdaRouteStage } from './LambdaRouteStage';

export * from './LambdaRouteStage';

if (SETTINGS.feature.lambdaAdditionalStages) {
  Registry.pipeline.registerStage(lambdaRouteStage);
}
