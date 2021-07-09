import { get } from 'lodash';
import React from 'react';

import {
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  IManifest,
  StageFailureMessage,
} from '@spinnaker/core';

import { ManifestStatus } from './ManifestStatus';
import { IStageManifest, KubernetesManifestService } from '../../../../manifest/manifest.service';

export interface IManifestSubscription {
  id: string;
  unsubscribe: () => void;
  manifest: IManifest;
}

export interface IDeployStatusState {
  subscriptions: IManifestSubscription[];
  manifestIds: string[];
}

export class DeployStatus extends React.Component<IExecutionDetailsSectionProps, IDeployStatusState> {
  public static title = 'deployStatus';

  constructor(props: IExecutionDetailsSectionProps) {
    super(props);
    this.state = { subscriptions: [], manifestIds: [] };
  }

  public componentDidMount() {
    this.componentDidUpdate(this.props, this.state);
  }

  public componentWillUnmount() {
    this.unsubscribeAll();
  }

  public componentDidUpdate(_prevProps: IExecutionDetailsSectionProps, prevState: IDeployStatusState) {
    const manifests: IStageManifest[] = get(this.props.stage, ['context', 'outputs.manifests'], []).filter((m) => !!m);
    const manifestIds = manifests.map((m) => KubernetesManifestService.manifestIdentifier(m)).sort();
    if (prevState.manifestIds.join('') !== manifestIds.join('')) {
      this.unsubscribeAll();
      const subscriptions = manifests.map((manifest) => {
        const id = KubernetesManifestService.manifestIdentifier(manifest);
        return {
          id,
          unsubscribe: this.subscribeToManifestUpdates(id, manifest),
          manifest: this.stageManifestToIManifest(manifest, this.props.stage.context.account),
        };
      });
      this.setState({ subscriptions, manifestIds });
    }
  }

  private subscribeToManifestUpdates(id: string, manifest: IStageManifest): () => void {
    const params = KubernetesManifestService.stageManifestToManifestParams(manifest, this.props.stage.context.account);
    return KubernetesManifestService.subscribe(this.props.application, params, (updated: IManifest) => {
      const idx = this.state.subscriptions.findIndex((sub) => sub.id === id);
      if (idx !== -1) {
        const subscription = { ...this.state.subscriptions[idx], manifest: updated };
        const subscriptions = [...this.state.subscriptions];
        subscriptions[idx] = subscription;
        this.setState({ subscriptions });
      }
    });
  }

  private unsubscribeAll() {
    this.state.subscriptions.forEach(({ unsubscribe }) => unsubscribe());
  }

  private stageManifestToIManifest(manifest: IStageManifest, account: string): IManifest {
    return {
      name: get(manifest, 'metadata.name', ''),
      moniker: null,
      account,
      cloudProvider: 'kubernetes',
      location: get(manifest, 'metadata.namespace', ''),
      manifest: manifest,
      status: {},
      artifacts: [],
      events: [],
    };
  }

  public render() {
    const { name: sectionName, current: currentSection, stage } = this.props;
    const manifests: IManifest[] = this.state.subscriptions.filter((sub) => !!sub.manifest).map((sub) => sub.manifest);
    return (
      <ExecutionDetailsSection name={sectionName} current={currentSection}>
        <StageFailureMessage stage={stage} message={stage.failureMessage} />
        {manifests && (
          <div className="row">
            <div className="col-md-12">
              <div className="well alert alert-info">
                {manifests.map((manifest) => {
                  const uid =
                    manifest.manifest.metadata.uid || KubernetesManifestService.manifestIdentifier(manifest.manifest);
                  return <ManifestStatus key={uid} manifest={manifest} account={stage.context.account} />;
                })}
              </div>
            </div>
          </div>
        )}
      </ExecutionDetailsSection>
    );
  }
}
