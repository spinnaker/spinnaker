import { get } from 'lodash';
import * as React from 'react';

import type { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common';
import { StageFailureMessage } from '../../../details';

export class EvaluateArtifactsExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'evaluateArtifactsConfig';

  public render() {
    const { current, name, stage } = this.props;
    const { outputs } = stage;
    const errorMessage = get(outputs, ['status', 'error'], '');
    return (
      <ExecutionDetailsSection name={name} current={current}>
        <StageFailureMessage stage={stage} message={errorMessage || stage.failureMessage} />
      </ExecutionDetailsSection>
    );
  }
}
