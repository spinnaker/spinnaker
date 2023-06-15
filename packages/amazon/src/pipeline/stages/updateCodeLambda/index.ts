// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { Registry, SETTINGS } from '@spinnaker/core';
import { lambdaUpdateCodeStage } from './LambdaUpdateCodeStage';

export * from './LambdaUpdateCodeStage';

if (SETTINGS.feature.lambdaAdditionalStages) {
  Registry.pipeline.registerStage(lambdaUpdateCodeStage);
}
