import * as React from 'react';

import {
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  StageExecutionLogs,
  StageFailureMessage,
} from '@spinnaker/core';

export function CloudfoundryDeployServiceExecutionDetails(props: IExecutionDetailsSectionProps) {
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />
      <StageExecutionLogs stage={props.stage} />
    </ExecutionDetailsSection>
  );
}

export namespace CloudfoundryDeployServiceExecutionDetails {
  export const title = 'cloudfoundryDeployServiceConfig';
}
