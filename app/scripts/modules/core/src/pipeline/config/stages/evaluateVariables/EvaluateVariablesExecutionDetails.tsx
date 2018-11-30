import * as React from 'react';

import {
  IExecutionDetailsSectionProps,
  ExecutionDetailsSection,
  StageExecutionLogs,
  StageFailureMessage,
} from 'core/pipeline';

import { IEvaluatedVariable } from './EvaluateVariablesStageConfig';

export function EvaluateVariablesExecutionDetails(props: IExecutionDetailsSectionProps) {
  const {
    stage: { context = {}, outputs = {} },
    stage,
    name,
    current,
  } = props;

  const evaluatedVariables = context.variables ? (
    <div>
      <dl className="dl-horizontal">
        {context.variables.map(({ key }: IEvaluatedVariable) => (
          <React.Fragment key={key}>
            <dt>{key}</dt>
            <dd>{outputs[key]}</dd>
          </React.Fragment>
        ))}
      </dl>
    </div>
  ) : (
    <div>
      <b>No variables were evaluated.</b>
    </div>
  );

  return (
    <ExecutionDetailsSection name={name} current={current}>
      {evaluatedVariables}
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      <StageExecutionLogs stage={stage} />
    </ExecutionDetailsSection>
  );
}

export namespace EvaluateVariablesExecutionDetails {
  export const title = 'evaluateVariablesConfig';
}
