import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';

export function PipelineParametersExecutionDetails(props: IExecutionDetailsSectionProps) {
  const {
    stage: { context = {} },
    name,
    current,
  } = props;

  const { pipelineParameters: parameters = {} } = context;

  return (
    <ExecutionDetailsSection name={name} current={current}>
      <div className="row">
        <div className="col-md-12">
          <h5>Parameters</h5>
          <dl>
            {Object.keys(parameters)
              .sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()))
              .map((key) => (
                <React.Fragment key={key}>
                  <dt>{key}</dt>
                  <dd>{parameters[key].toString()}</dd>
                </React.Fragment>
              ))}
          </dl>
        </div>
      </div>
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace PipelineParametersExecutionDetails {
  export const title = 'parameters';
}
