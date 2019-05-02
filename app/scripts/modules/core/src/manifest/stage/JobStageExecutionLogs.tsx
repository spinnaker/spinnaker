import * as React from 'react';

import { IManifestSubscription } from '../IManifestSubscription';
import { IStageManifest, ManifestService } from '../ManifestService';
import { JobManifestPodLogs } from './JobManifestPodLogs';
import { IManifest } from 'core/domain/IManifest';

import { get } from 'lodash';
import { Application } from 'core/application';

interface IJobStageExecutionLogsProps {
  manifest: IStageManifest;
  deployedName: string;
  account: string;
  application: Application;
}

interface IJobStageExecutionLogsState {
  subscription: IManifestSubscription;
  manifestId: string;
}

export class JobStageExecutionLogs extends React.Component<IJobStageExecutionLogsProps, IJobStageExecutionLogsState> {
  public state = {
    subscription: { id: '', unsubscribe: () => {}, manifest: {} } as IManifestSubscription,
    manifestId: '',
  };

  public componentDidMount() {
    this.componentDidUpdate(this.props, this.state);
  }

  public componentWillMount() {
    this.unsubscribe();
  }

  private unsubscribe() {
    this.state.subscription && this.state.subscription.unsubscribe && this.state.subscription.unsubscribe();
  }

  public componentDidUpdate(_prevPropds: IJobStageExecutionLogsProps, prevState: IJobStageExecutionLogsState) {
    const { manifest } = this.props;
    const manifestId = ManifestService.manifestIdentifier(manifest);
    if (prevState.manifestId === manifestId) {
      return;
    }
    this.refreshSubscription(manifestId, manifest);
  }

  private refreshSubscription(manifestId: string, manifest: IStageManifest) {
    const subscription = {
      id: manifestId,
      manifest: this.stageManifestToIManifest(manifest, this.props.deployedName, this.props.account),
      unsubscribe: this.subscribeToManifestUpdates(manifest),
    };
    this.setState({ subscription, manifestId });
  }

  private subscribeToManifestUpdates(manifest: IStageManifest): () => void {
    const params = {
      account: this.props.account,
      name: this.props.deployedName,
      location: manifest.metadata.namespace == null ? '_' : manifest.metadata.namespace,
    };
    return ManifestService.subscribe(this.props.application, params, (updated: IManifest) => {
      const subscription = { ...this.state.subscription, manifest: updated };
      this.setState({ subscription });
    });
  }

  private stageManifestToIManifest(manifest: IStageManifest, deployedName: string, account: string): IManifest {
    const namespace = get(manifest, ['metadata', 'namespace'], '');

    return {
      name: deployedName,
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
    const { manifest } = this.state.subscription;
    let event: any = null;
    if (manifest && manifest.events) {
      event = manifest.events.find((e: any) => e.message.startsWith('Created pod'));
    }
    if (!manifest || !event) {
      return <div>No Console Output</div>;
    }

    return <JobManifestPodLogs manifest={manifest} manifestEvent={event} linkName="Console Output" />;
  }
}
