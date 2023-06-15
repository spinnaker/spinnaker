// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import { Registry, SETTINGS } from '@spinnaker/core';
import { lambdaDeploymentStage } from './config/LambdaDeploymentStage';

export * from './config/LambdaDeploymentStage';

if (SETTINGS.feature.lambdaAdditionalStages) {
  Registry.pipeline.registerStage(lambdaDeploymentStage);
}
