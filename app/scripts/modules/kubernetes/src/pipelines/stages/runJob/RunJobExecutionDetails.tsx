import { get, last, sortBy } from 'lodash';
import React from 'react';

import {
  AccountTag,
  DefaultPodNameProvider,
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  IJobOwnedPodStatus,
  JobStageExecutionLogs,
  StageFailureMessage,
} from '@spinnaker/core';

export class RunJobExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'runJobConfig';

  private mostRecentlyCreatedPodName(podsStatuses: IJobOwnedPodStatus[]): string {
    const sorted = sortBy(podsStatuses, (p: IJobOwnedPodStatus) => p.status.startTime);
    const mostRecent = last(sorted);
    return mostRecent ? mostRecent.name : '';
  }

  public render() {
    const { stage, name, current } = this.props;
    const { context } = stage;

    const namespace = get(stage, ['context', 'jobStatus', 'location'], '');
    const deployedName = namespace ? get<string[]>(context, ['deploy.jobs', namespace])[0] : '';
    const externalLink = get<string>(stage, ['context', 'execution', 'logs']);
    const podName = this.mostRecentlyCreatedPodName(get(stage.context, ['jobStatus', 'pods'], []));
    const podNameProvider = new DefaultPodNameProvider(podName);

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <StageFailureMessage stage={stage} message={stage.failureMessage} />
        <div className="row">
          <div className="col-md-9">
            <dl className="dl-narrow dl-horizontal">
              <dt>Account</dt>
              <dd>
                <AccountTag account={context.account} />
              </dd>
              {namespace && (
                <>
                  <dt>Namespace</dt>
                  <dd>{stage.context.jobStatus.location}</dd>
                  <dt>Logs</dt>
                  <dd>
                    <JobStageExecutionLogs
                      deployedName={deployedName}
                      account={this.props.stage.context.account}
                      location={namespace}
                      application={this.props.application}
                      externalLink={externalLink}
                      podNameProvider={podNameProvider}
                    />
                  </dd>
                </>
              )}
              {!namespace && <div className="well">Collecting additional details...</div>}
            </dl>
          </div>
        </div>
      </ExecutionDetailsSection>
    );
  }
}
