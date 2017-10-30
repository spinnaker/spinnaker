import * as React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from './ExecutionDetailsSection';
import { ExecutionStepDetails } from './ExecutionStepDetails';

export function ExecutionDetailsTasks(props: IExecutionDetailsSectionProps) {
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <ExecutionStepDetails item={props.stage} />
    </ExecutionDetailsSection>
  );
}

export namespace ExecutionDetailsTasks {
  export const title = 'taskStatus';
}
