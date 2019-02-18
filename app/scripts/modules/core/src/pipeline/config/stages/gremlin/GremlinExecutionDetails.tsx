import * as React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps, StageFailureMessage } from 'core/pipeline';

export function GremlinExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const {
    context: { gremlinCommandTemplateId, gremlinTargetTemplateId, gremlinApiKey },
  } = stage;

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-12">
          <h5>Gremlin Stage Configuration</h5>
          <dl className="dl-narrow dl-horizontal">
            {gremlinCommandTemplateId && <dt>Command Template ID</dt>}
            {gremlinCommandTemplateId && <dd>{gremlinCommandTemplateId}</dd>}
          </dl>
          <dl className="dl-narrow dl-horizontal">
            {gremlinTargetTemplateId && <dt>Target Template ID</dt>}
            {gremlinTargetTemplateId && <dd>{gremlinTargetTemplateId}</dd>}
          </dl>
          <dl className="dl-narrow dl-horizontal">
            {gremlinApiKey && <dt>API Key</dt>}
            {gremlinApiKey && <dd>{gremlinApiKey}</dd>}
          </dl>
        </div>
      </div>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
    </ExecutionDetailsSection>
  );
}

// eslint-disable-next-line
export namespace GremlinExecutionDetails {
  export const title = 'gremlinConfig';
}
