import * as React from 'react';

import {
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  StageExecutionLogs,
  StageFailureMessage,
} from 'core/pipeline';
import { AccountTag } from 'core/account';
import { ServerGroupStageContext } from '../common/ServerGroupStageContext';

export function DisableClusterExecutionDetails(props: IExecutionDetailsSectionProps) {
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
            <dt>Keep Enabled</dt>
            <dd>{stage.context.remainingEnabledServerGroups}</dd>
          </dl>
        </div>
      </div>
      <ServerGroupStageContext serverGroups={stage.context['deploy.server.groups']} />

      <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />
      <StageExecutionLogs stage={props.stage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace DisableClusterExecutionDetails {
  export const title = 'disableClusterConfig';
}
