import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageFailureMessage } from '../../../details';

export function GremlinExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
    </ExecutionDetailsSection>
  );
}

// eslint-disable-next-line
export namespace GremlinExecutionDetails {
  export const title = 'gremlinConfig';
}
