// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { Registry, SETTINGS } from '@spinnaker/core';

import { lambdaDeleteStage } from './LambdaDeleteStage';
export * from './LambdaDeleteStage';

export function registerLambdaDeleteStage(): void {
  if (SETTINGS.feature.lambdaAdditionalStages) {
    Registry.pipeline.registerStage(lambdaDeleteStage);
  }
}
