import * as React from 'react';

import {
  ExecutionDetailsSection,
  ExecutionStepDetails,
  IExecutionDetailsSectionProps,
  StageFailureMessage,
} from '@spinnaker/core';

export class DeployExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'Task Status';

  constructor(props: IExecutionDetailsSectionProps) {
    super(props);
  }

  public render() {
    const { stage, current, name } = this.props;

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <ExecutionStepDetails item={stage} />
        <StageFailureMessage stage={stage} message={stage.failureMessage} />
      </ExecutionDetailsSection>
    );
  }
}
