import * as React from 'react';

import {
  IExecutionDetailsSectionProps,
  ExecutionDetailsSection,
  StageExecutionLogs,
  StageFailureMessage,
} from 'core/pipeline';

export function ConcourseExecutionDetails(props: IExecutionDetailsSectionProps) {
  const {
    stage: { context = {} },
    stage,
    name,
    current,
  } = props;

  return (
    <ExecutionDetailsSection name={name} current={current}>
      <dl className="dl-narrow dl-horizontal">
        <dt>Master</dt>
        <dd>{context.master}</dd>
        <dt>Team</dt>
        <dd>{context.teamName}</dd>
        <dt>Pipeline</dt>
        <dd>{context.pipelineName}</dd>
        <dt>Job</dt>
        <dd>{context.jobName}</dd>
        <dt>Build</dt>
        <dd>{context.buildNumber}</dd>
      </dl>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      <StageExecutionLogs stage={stage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace ConcourseExecutionDetails {
  export const title = 'concourseConfig';
}
