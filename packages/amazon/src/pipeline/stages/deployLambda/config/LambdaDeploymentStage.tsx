// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import type { IStageTypeConfig } from '@spinnaker/core';
import { ExecutionDetailsTasks, HelpContentsRegistry } from '@spinnaker/core';

import { LambdaDeploymentConfig, validate } from './LambdaDeploymentStageConfig';
import { LambdaDeploymentExecutionDetails } from './LambdaDeploymentStageExecutionDetails';

export const initialize = () => {
  HelpContentsRegistry.register('aws.lambdaDeploymentStage.lambda', 'Lambda Name');
};

export const lambdaDeploymentStage: IStageTypeConfig = {
  key: 'Aws.LambdaDeploymentStage',
  label: `AWS Lambda Deployment`,
  description: 'Create a Single AWS Lambda Function',
  component: LambdaDeploymentConfig, // stage config
  executionDetailsSections: [LambdaDeploymentExecutionDetails, ExecutionDetailsTasks],
  validateFn: validate,
};
