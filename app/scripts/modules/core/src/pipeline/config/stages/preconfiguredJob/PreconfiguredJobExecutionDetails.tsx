import * as React from 'react';

import {
  IExecutionDetailsSectionProps,
  ExecutionDetailsSection,
  StageExecutionLogs,
  StageFailureMessage,
} from 'core/pipeline';

export function PreconfiguredJobExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage, name, current } = props;

  return (
    <ExecutionDetailsSection name={name} current={current}>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      <StageExecutionLogs stage={stage} />
    </ExecutionDetailsSection>
  );
}

export namespace PreconfiguredJobExecutionDetails {
  export const title = 'preconfiguredJobConfig';
}
