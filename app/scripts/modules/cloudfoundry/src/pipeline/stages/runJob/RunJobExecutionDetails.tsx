import { get } from 'lodash';
import React from 'react';

import { AccountTag, ExecutionDetailsSection, IExecutionDetailsSectionProps } from '@spinnaker/core';
import {
  CloudFoundryRecentLogs,
  CloudFoundryRecentLogsType,
} from '../../../presentation/widgets/recentLogs/CloudFoundryRecentLogs';

export class RunJobExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'runJobDetails';

  public render() {
    const { stage, name, current } = this.props;
    const { context } = stage;
    const account = context['deploy.account.name'];
    const region = get(stage, ['context', 'jobStatus', 'location'], '');
    const taskId = get(stage, ['context', 'jobStatus', 'id'], '');
    const taskName = get(stage, ['context', 'jobStatus', 'name'], '');
    const logsUrl: string = get(stage, ['context', 'logsUrl']);

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-9">
            <dl className="dl-narrow dl-horizontal">
              <dt>Account</dt>
              <dd>
                <AccountTag account={account} />
              </dd>
              {region && (
                <>
                  <dt>Region</dt>
                  <dd>{region}</dd>
                  <dt>Logs</dt>
                  <dd>
                    <CloudFoundryRecentLogs
                      account={account}
                      region={region}
                      resourceDisplayName={taskName}
                      resourceGuid={taskId}
                      logsType={CloudFoundryRecentLogsType.TASK}
                    />
                  </dd>
                  {logsUrl && (
                    <dd>
                      <a target="_blank" href={logsUrl}>
                        External
                      </a>
                    </dd>
                  )}
                </>
              )}
              {!region && <div className="well">Collecting additional details...</div>}
            </dl>
          </div>
        </div>
      </ExecutionDetailsSection>
    );
  }
}
