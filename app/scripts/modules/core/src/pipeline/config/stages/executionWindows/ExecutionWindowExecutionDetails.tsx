import * as React from 'react';

import {
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  StageExecutionLogs,
  StageFailureMessage,
} from 'core/pipeline';
import { ExecutionWindowActions } from './ExecutionWindowActions';

export function ExecutionWindowExecutionDetails(props: IExecutionDetailsSectionProps) {
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <ExecutionWindowActions application={props.application} execution={props.execution} stage={props.stage} />
      <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />
      <StageExecutionLogs stage={props.stage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace ExecutionWindowExecutionDetails {
  export const title = 'windowConfig';
}
