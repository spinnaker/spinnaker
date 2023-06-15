// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import React from 'react';

import type { IExecutionDetailsSectionProps } from '@spinnaker/core';
import { ExecutionDetailsSection, StageFailureMessage } from '@spinnaker/core';

export function LambdaDeploymentExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage, current, name } = props;
  return (
    <ExecutionDetailsSection name={name} current={current}>
      <StageFailureMessage stage={stage} message={stage.outputs.failureMessage} />
      <div>
        <p>
          {' '}
          <b> Function Name: </b> {stage.outputs.functionName ? stage.outputs.functionName : 'N/A'}{' '}
        </p>
        <p>
          {' '}
          <b> Function ARN: </b> {stage.outputs.functionARN ? stage.outputs.functionARN : 'N/A'}{' '}
        </p>
      </div>
    </ExecutionDetailsSection>
  );
}

// eslint-disable-next-line
export namespace LambdaDeploymentExecutionDetails {
  export const title = 'Lambda Deployment Stage';
}
