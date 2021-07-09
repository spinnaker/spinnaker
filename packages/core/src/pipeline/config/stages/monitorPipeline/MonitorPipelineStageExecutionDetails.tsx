import { UISref } from '@uirouter/react';
import _ from 'lodash';
import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageFailureMessage } from '../../../details';

export function MonitorPipelineStageExecutionDetails(props: IExecutionDetailsSectionProps) {
  const {
    stage: { context = {}, outputs = {} },
    stage,
    name,
    current,
  } = props;

  const statuses = _.sortBy(
    Object.keys(outputs.executionStatuses).map((executionId) => {
      const status = { ...outputs.executionStatuses[executionId] };
      status.executionId = executionId;

      const generalException: any = status.exception;
      if (generalException) {
        const errors = (generalException.details?.errors ?? []).filter((m: any) => !!m);

        if (errors.length) {
          status.failureMessage = errors.join('\n\n');
        } else {
          status.failureMessage = generalException.details?.error ?? null;
        }
      }

      return status;
    }),
    'status',
  );

  return (
    <ExecutionDetailsSection name={name} current={current}>
      <div className="row">
        <div className="col-md-12">
          <h5>Monitor behavior</h5>
          {context.monitorBehavior}

          <h5>Executions</h5>
          {statuses.map((status) => {
            return (
              <div key={status.executionId}>
                <span className={'label label-default label-' + status.status.toLowerCase()}>{status.status}</span>{' '}
                <UISref
                  key={status.executionId}
                  to="home.applications.application.pipelines.executionDetails.execution"
                  params={{
                    application: status.application,
                    executionId: status.executionId,
                  }}
                  options={{ inherit: false, reload: 'home.applications.application.pipelines.executionDetails' }}
                >
                  <a>{status.executionId}</a>
                </UISref>{' '}
                ({status.application})
                {status.failureMessage && <StageFailureMessage stage={stage} message={status.failureMessage} />}
              </div>
            );
          })}
        </div>
      </div>
      <br />
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace MonitorPipelineStageExecutionDetails {
  export const title = 'monitorPipelineConfig';
}
