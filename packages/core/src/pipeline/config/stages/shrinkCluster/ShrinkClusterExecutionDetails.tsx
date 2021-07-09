import React from 'react';

import { AccountTag } from '../../../../account';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { ServerGroupStageContext } from '../common/ServerGroupStageContext';
import { StageExecutionLogs, StageFailureMessage } from '../../../details';

export function ShrinkClusterExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-9">
          <dl className="dl-narrow dl-horizontal">
            <dt>Account</dt>
            <dd>
              <AccountTag account={stage.context.credentials} />
            </dd>
            <dt>Region</dt>
            <dd>{stage.context.region || (stage.context.regions || []).join(', ')}</dd>
            <dt>Cluster</dt>
            <dd>{stage.context.cluster}</dd>
            <dt>Shrink to Size</dt>
            <dd>{stage.context.shrinkToSize}</dd>
            <dt>Delete Active?</dt>
            <dd>{String(!!stage.context.allowDeleteActive)}</dd>
          </dl>
        </div>
      </div>
      <ServerGroupStageContext status="Shrunk" serverGroups={stage.context['deploy.server.groups']} />
      <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />
      <StageExecutionLogs stage={props.stage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace ShrinkClusterExecutionDetails {
  export const title = 'shrinkClusterConfig';
}
