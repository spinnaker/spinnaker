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

import { DeleteLambdaFunctionStageForm } from './DeleteLambdaFunctionStageForm';

export function DeleteLambdaExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage, name, current } = props;
  return (
    <ExecutionDetailsSection name={name} current={current}>
      <StageFailureMessage stage={stage} message={stage.outputs.failureMessage} />
      <div>
        <p>
          {' '}
          <b> Status: </b> {stage.outputs.deleteTask === 'done' ? 'COMPLETE' : stage.outputs.deleteTask}{' '}
        </p>
        <p>
          {' '}
          <b> Deleted Version: </b>{' '}
          {stage.outputs['deleteTask:deleteVersion'] ? stage.outputs['deleteTask:deleteVersion'] : 'N/A'}{' '}
        </p>
      </div>
    </ExecutionDetailsSection>
  );
}

function DeleteLambdaConfig(props: IStageConfigProps) {
  return (
    <div className="DeleteLambdaStageConfig">
      <FormikStageConfig
        {...props}
        validate={validate}
        onChange={props.updateStage}
        render={(props: IFormikStageConfigInjectedProps) => <DeleteLambdaFunctionStageForm {...props} />}
      />
    </div>
  );
}

export const initialize = () => {
  HelpContentsRegistry.register('aws.lambdaDeploymentStage.lambda', 'Lambda Name');
};

function validate(stageConfig: IStage) {
  const validator = new FormValidator(stageConfig);
  validator.field('account', 'Account Name').required();

  validator.field('region', 'Region').required();

  validator.field('functionName', 'Lambda Function Name').required();

  validator.field('version', 'Lambda Function Version').required();

  return validator.validateForm();
}

// eslint-disable-next-line
export namespace DeleteLambdaExecutionDetails {
  export const title = 'Delete Lambda Stage';
}

export const lambdaDeleteStage: IStageTypeConfig = {
  key: 'Aws.LambdaDeleteStage',
  label: `AWS Lambda Delete`,
  description: 'Delete an AWS Lambda Function',
  component: DeleteLambdaConfig, // stage config
  executionDetailsSections: [DeleteLambdaExecutionDetails, ExecutionDetailsTasks],
  validateFn: validate,
};
