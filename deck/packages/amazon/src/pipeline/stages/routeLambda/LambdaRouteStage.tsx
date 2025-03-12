// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import React from 'react';

import type {
  IExecutionDetailsSectionProps,
  IFormikStageConfigInjectedProps,
  IStage,
  IStageConfigProps,
  IStageTypeConfig,
} from '@spinnaker/core';
import {
  ExecutionDetailsSection,
  ExecutionDetailsTasks,
  FormikStageConfig,
  FormValidator,
  HelpContentsRegistry,
  StageFailureMessage,
} from '@spinnaker/core';

import { RouteLambdaFunctionStageForm } from './RouteLambdaFunctionStageForm';
import { awsArnValidator } from '../../../aws.validators';

export function RouteLambdaExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <StageFailureMessage stage={stage} message={stage.outputs.failureMessage} />
      <div>
        <p> Function Name: {stage.outputs.functionName ? stage.outputs.functionName : 'N/A'} </p>
        <p>
          {' '}
          Deployed Alias:{' '}
          {stage.outputs['deployment:aliasDeployed'] ? stage.outputs['deployment:aliasDeployed'] : 'N/A'}{' '}
        </p>
        <p>
          {' '}
          Deployed Major Version:{' '}
          {stage.outputs['deployment:majorVersionDeployed']
            ? stage.outputs['deployment:majorVersionDeployed']
            : 'N/A'}{' '}
        </p>
      </div>
    </ExecutionDetailsSection>
  );
}

function RouteLambdaConfig(props: IStageConfigProps) {
  return (
    <div className="RouteLambdaStageConfig">
      <FormikStageConfig
        {...props}
        validate={validate}
        onChange={props.updateStage}
        render={(props: IFormikStageConfigInjectedProps) => <RouteLambdaFunctionStageForm {...props} />}
      />
    </div>
  );
}

export const initialize = () => {
  HelpContentsRegistry.register('aws.lambdaDeploymentStage.lambda', 'Lambda Name');
};

function validate(stageConfig: IStage) {
  const validator = new FormValidator(stageConfig);

  validator
    .field('triggerArns', 'Trigger ARNs')
    .optional()
    .withValidators((value: any, _: string) => {
      const tmp: any[] = value.map((arn: string) => {
        return awsArnValidator(arn, arn);
      });
      const ret: boolean = tmp.every((el) => el === undefined);
      return ret
        ? undefined
        : 'Invalid ARN. Event ARN must match regular expression: /^arn:aws[a-zA-Z-]?:[a-zA-Z_0-9.-]+:./';
    });

  return validator.validateForm();
}

// eslint-disable-next-line
export namespace RouteLambdaExecutionDetails {
  export const title = 'Route Lambda Traffic Stage';
}

export const lambdaRouteStage: IStageTypeConfig = {
  key: 'Aws.LambdaTrafficRoutingStage',
  label: `AWS Lambda Route`,
  description: 'Route traffic across various versions of your Lambda function',
  component: RouteLambdaConfig, // stage config
  executionDetailsSections: [RouteLambdaExecutionDetails, ExecutionDetailsTasks],
  validateFn: validate,
};
