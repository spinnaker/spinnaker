import * as React from 'react';
import { get } from 'lodash';

import {
  IExecutionDetailsSectionProps,
  ExecutionDetailsSection,
  AccountTag,
  JobStageExecutionLogs,
  IStageManifest,
} from '@spinnaker/core';

interface IStageDeployedJobs {
  [namespace: string]: string[];
}

export class RunJobExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'runJobConfig';

  private extractDeployedJobName(manifest: IStageManifest, deployedJobs: IStageDeployedJobs): string {
    const namespace = get(manifest, ['metadata', 'namespace'], '');
    const jobNames = get(deployedJobs, namespace, []);
    return jobNames.length > 0 ? jobNames[0] : '';
  }

  public render() {
    const { stage, name, current } = this.props;
    const { context } = stage;

    const { manifest } = context;
    const deployedName = this.extractDeployedJobName(manifest, get(context, ['deploy.jobs']));

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-9">
            <dl className="dl-narrow dl-horizontal">
              <dt>Account</dt>
              <dd>
                <AccountTag account={context.account} />
              </dd>
              {stage.context.jobStatus && stage.context.jobStatus.location && (
                <span>
                  <dt>Namespace</dt>
                  <dd>{stage.context.jobStatus.location}</dd>
                </span>
              )}
              <dt>Logs</dt>
              <dd>
                <JobStageExecutionLogs
                  manifest={manifest}
                  deployedName={deployedName}
                  account={this.props.stage.context.account}
                  application={this.props.application}
                />
              </dd>
            </dl>
          </div>
        </div>
      </ExecutionDetailsSection>
    );
  }
}
