import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from './ExecutionDetailsSection';
import { ExecutionStepDetails } from './ExecutionStepDetails';

export function ExecutionDetailsTasks(props: IExecutionDetailsSectionProps) {
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <ExecutionStepDetails item={props.stage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace ExecutionDetailsTasks {
  export const title = 'taskStatus';
}
