import * as React from 'react';
import { get } from 'lodash';

import { IExecutionDetailsSectionProps, ExecutionDetailsSection, AccountTag, IManifest } from '@spinnaker/core';

import { JobManifestPodLogs } from '../deployManifest/react/JobManifestPodLogs';

import { KubernetesManifestService, IStageManifest } from 'kubernetes/v2/manifest/manifest.service';

import { IManifestSubscription } from '../deployManifest/react/DeployStatus';

interface IStageDeployedJobs {
  [namespace: string]: string[];
}

interface IRunJobExecutionDetailsState {
  subscription: IManifestSubscription;
  manifestId: string;
}

export class RunJobExecutionDetails extends React.Component<
  IExecutionDetailsSectionProps,
  IRunJobExecutionDetailsState
> {
  public static title = 'runJobConfig';
  public state = {
    subscription: { id: '', unsubscribe: () => {}, manifest: {} } as IManifestSubscription,
    manifestId: '',
  };

  public componentDidMount() {
    this.componentDidUpdate(this.props, this.state);
  }

  public componentWillUnmount() {
    this.unsubscribe();
  }

  public componentDidUpdate(_prevProps: IExecutionDetailsSectionProps, prevState: IRunJobExecutionDetailsState) {
    const manifest: IStageManifest = get(this.props.stage, ['context', 'manifest']);
    const manifestId = KubernetesManifestService.manifestIdentifier(manifest);
    if (prevState.manifestId !== manifestId) {
      this.unsubscribe();
      const subscription = {
        id: manifestId,
        unsubscribe: this.subscribeToManifestUpdates(manifest),
        manifest: this.stageManifestToIManifest(
          manifest,
          get(this.props.stage, ['context', 'deploy.jobs'], {}),
          this.props.stage.context.account,
        ),
      };
      this.setState({
        subscription,
        manifestId,
      });
    }
  }

  private unsubscribe() {
    this.state.subscription && this.state.subscription.unsubscribe && this.state.subscription.unsubscribe();
  }

  private subscribeToManifestUpdates(manifest: IStageManifest): () => void {
    const params = {
      account: this.props.stage.context.account,
      name: this.extractDeployedJobName(manifest, get(this.props.stage, ['context', 'deploy.jobs'], {})),
      location: manifest.metadata.namespace == null ? '_' : manifest.metadata.namespace,
    };
    return KubernetesManifestService.subscribe(this.props.application, params, (updated: IManifest) => {
      const subscription = { ...this.state.subscription, manifest: updated };
      this.setState({ subscription });
    });
  }

  private extractDeployedJobName(manifest: IStageManifest, deployedJobs: IStageDeployedJobs): string {
    const namespace = get(manifest, ['metadata', 'namespace'], '');
    const jobNames = get(deployedJobs, namespace, []);
    return jobNames.length > 0 ? jobNames[0] : '';
  }

  private stageManifestToIManifest(
    manifest: IStageManifest,
    deployedJobs: IStageDeployedJobs,
    account: string,
  ): IManifest {
    const namespace = get(manifest, ['metadata', 'namespace'], '');
    const name = this.extractDeployedJobName(manifest, deployedJobs);

    return {
      name,
      moniker: null,
      account,
      cloudProvider: 'kubernetes',
      location: namespace,
      manifest,
      status: {},
      artifacts: [],
      events: [],
    };
  }

  public render() {
    const { stage, name, current } = this.props;
    const { context } = stage;
    const manifest = get(this.state, ['subscription', 'manifest'], null);
    let event: any = null;
    if (manifest && manifest.events) {
      event = manifest.events.find((e: any) => e.message.startsWith('Created pod'));
    }
    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-9">
            <dl className="dl-narrow dl-horizontal">
              <dt>Account</dt>
              <dd>
                <AccountTag account={context.account} />
              </dd>
            </dl>
            {stage.context.jobStatus && stage.context.jobStatus.location && (
              <dl className="dl-narrow dl-horizontal">
                <dt>Namespace</dt>
                <dd>{stage.context.jobStatus.location}</dd>
              </dl>
            )}
          </div>
          {manifest && event && (
            <div className="col-md-9 well">
              <JobManifestPodLogs manifest={manifest} manifestEvent={event} linkName="Console Output (Raw)" />
            </div>
          )}
        </div>
      </ExecutionDetailsSection>
    );
  }
}
