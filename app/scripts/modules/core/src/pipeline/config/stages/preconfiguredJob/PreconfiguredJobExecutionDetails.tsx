import * as React from 'react';

import {
  IExecutionDetailsSectionProps,
  ExecutionDetailsSection,
  StageExecutionLogs,
  StageFailureMessage,
} from 'core/pipeline';
import { IPreconfiguredJobParameter } from './preconfiguredJobStage';

export function PreconfiguredJobExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage, name, current } = props;

  const parameters =
    stage.context.preconfiguredJobParameters && stage.context.parameters ? (
      <div>
        <dl className="dl-horizontal">
          {stage.context.preconfiguredJobParameters.map((parameter: IPreconfiguredJobParameter) => (
            <React.Fragment>
              <dt>{parameter.label}</dt>
              <dd>{stage.context.parameters[parameter.name]}</dd>
            </React.Fragment>
          ))}
        </dl>
      </div>
    ) : (
      <div>No details provided.</div>
    );

  return (
    <ExecutionDetailsSection name={name} current={current}>
      {parameters}
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      <StageExecutionLogs stage={stage} />
    </ExecutionDetailsSection>
  );
}

export namespace PreconfiguredJobExecutionDetails {
  export const title = 'preconfiguredJobConfig';
}
