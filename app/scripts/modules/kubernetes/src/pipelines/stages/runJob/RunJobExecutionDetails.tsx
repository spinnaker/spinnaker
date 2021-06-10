import { get, isEmpty, sortBy } from 'lodash';
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

export class RunJobExecutionDetails extends React.Component<IExecutionDetailsSectionProps, any> {
  public static title = 'runJobConfig';

  private createdPodNames(podsStatuses: IJobOwnedPodStatus[]): string[] {
    const sorted = sortBy(podsStatuses, (p: IJobOwnedPodStatus) => p.status.startTime);
    return sorted.map((p: IJobOwnedPodStatus) => p.name);
  }

  public render() {
    const { stage, name, current } = this.props;
    const { context } = stage;
    const namespace = get(stage, ['context', 'jobStatus', 'location'], '');
    const deployedName = namespace ? get<string[]>(context, ['deploy.jobs', namespace])[0] : '';
    const externalLink = get<string>(stage, ['context', 'execution', 'logs']);
    const pods = get(stage.context, 'jobStatus.pods', []);
    const podNames = !isEmpty(pods)
      ? this.createdPodNames(pods)
      : [get(stage, ['context', 'jobStatus', 'mostRecentPodName'], '')];
    const podNamesProviders = podNames.map((p) => new DefaultPodNameProvider(p));

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
                      podNamesProviders={podNamesProviders}
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
