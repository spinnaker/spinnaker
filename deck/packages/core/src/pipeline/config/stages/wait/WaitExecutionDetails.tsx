import React from 'react';

import { SkipWait } from './SkipWait';
import type { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common';
import { StageExecutionLogs, StageFailureMessage } from '../../../details';

export function WaitExecutionDetails(props: IExecutionDetailsSectionProps) {
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <SkipWait application={props.application} execution={props.execution} stage={props.stage} />
      <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />
      <StageExecutionLogs stage={props.stage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace WaitExecutionDetails {
  export const title = 'waitConfig';
}
