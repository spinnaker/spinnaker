import * as React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps, StageFailureMessage } from 'core/pipeline';

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
