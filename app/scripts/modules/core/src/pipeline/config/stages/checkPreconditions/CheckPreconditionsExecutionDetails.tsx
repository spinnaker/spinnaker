import { get, isBoolean, isString } from 'lodash';
import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageFailureMessage } from '../../../details';
import { robotToHuman } from '../../../../presentation/robotToHumanFilter/robotToHuman.filter';

export function CheckPreconditionsExecutionDetails(props: IExecutionDetailsSectionProps) {
  const context = get(props.stage, 'context.context', {} as any);
  const userFailureMessage = context.failureMessage;
  const failureMessage = props.stage.failureMessage;
  const stageFailureMessage = failureMessage == null ? userFailureMessage : failureMessage;
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-12">
          <dl className="dl-horizontal">
            {Object.keys(context)
              .filter((key) => !['failureMessage'].includes(key) && context[key] !== null)
              .map((key) => (
                <div key={key}>
                  <dt>{robotToHuman(key)}</dt>
                  <dd>
                    {isString(context[key]) || isBoolean(context[key]) ? context[key] : JSON.stringify(context[key])}
                  </dd>
                </div>
              ))}
            <div>
              <dt>Fail Pipeline</dt>
              <dd>{String(!!props.stage.context.failPipeline)}</dd>
            </div>
          </dl>
        </div>
      </div>
      {props.stage.status !== 'SUCCEEDED' && <StageFailureMessage stage={props.stage} message={stageFailureMessage} />}
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace CheckPreconditionsExecutionDetails {
  export const title = 'checkPreconditions';
}
