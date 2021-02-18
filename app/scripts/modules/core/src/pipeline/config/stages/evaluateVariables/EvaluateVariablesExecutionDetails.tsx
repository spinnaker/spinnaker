import React from 'react';

import { IEvaluatedVariable } from './EvaluateVariablesStageConfig';
import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageExecutionLogs, StageFailureMessage } from '../../../details';
import { Markdown } from '../../../../presentation';

export function EvaluateVariablesExecutionDetails(props: IExecutionDetailsSectionProps) {
  const {
    stage: { context = {}, outputs = {} },
    stage,
    name,
    current,
  } = props;

  const evaluatedVariables = context.variables ? (
    <div>
      <dl>
        {context.variables.map(({ key }: IEvaluatedVariable) => (
          <React.Fragment key={key}>
            <dt>{key}</dt>
            <dd>
              <Markdown message={'```\n' + JSON.stringify(outputs[key], null, 2) + '\n```'} />
            </dd>
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

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace EvaluateVariablesExecutionDetails {
  export const title = 'evaluateVariablesConfig';
}
