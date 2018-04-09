import * as React from 'react';

import { StageExecutionLogs, StageFailureMessage } from 'core/pipeline/details';
import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from 'core/pipeline/config/stages/core';
import { SkipWait } from './SkipWait';

export function WaitExecutionDetails(props: IExecutionDetailsSectionProps) {
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <SkipWait application={props.application} execution={props.execution} stage={props.stage} />
      <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />
      <StageExecutionLogs stage={props.stage} />
    </ExecutionDetailsSection>
  );
}

export namespace WaitExecutionDetails {
  export const title = 'waitConfig';
}
