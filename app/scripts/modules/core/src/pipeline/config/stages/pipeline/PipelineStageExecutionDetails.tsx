import * as React from 'react';
import { UISref } from '@uirouter/react';

import { IExecutionDetailsSectionProps, ExecutionDetailsSection, StageFailureMessage } from 'core/pipeline';

export function PipelineStageExecutionDetails(props: IExecutionDetailsSectionProps) {
  const {
    application,
    execution,
    stage: { context = {} },
    stage,
    name,
    current,
  } = props;

  return (
    <ExecutionDetailsSection name={name} current={current}>
      <div className="row">
        <div className="col-md-12">
          <h5>Pipeline Stage Configuration</h5>
          <dl className="dl-narrow dl-horizontal">
            <dt>Application</dt>
            <dd>{context.application}</dd>
            <dt>Pipeline</dt>
            <dd>{context.executionName}</dd>
            <dt>Status</dt>
            <dd>{context.status}</dd>
          </dl>
        </div>
      </div>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      <div className="row">
        <div className="col-md-12">
          <div className="well alert alert-info">
            <UISref
              to="home.applications.application.pipelines.executionDetails.execution"
              params={{
                application: stage.context.application,
                executionId: stage.context.executionId,
                executionParams: { application: application.name, executionId: execution.id },
              }}
              options={{ inherit: false, reload: 'home.applications.application.pipelines.executionDetails' }}
            >
              <a target="_self">View Pipeline Execution</a>
            </UISref>
          </div>
        </div>
      </div>
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace PipelineStageExecutionDetails {
  export const title = 'pipelineConfig';
}
