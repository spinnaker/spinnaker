import * as React from 'react';
import { get, upperFirst } from 'lodash';
import {
  IExecutionDetailsSectionProps,
  ExecutionDetailsSection,
  StageFailureMessage,
  IManifest,
} from '@spinnaker/core';

import { KubernetesManifestService, IStageManifest } from 'kubernetes/v2/manifest/manifest.service';

import { ManifestStatus } from './ManifestStatus';

// from https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/
const BUILT_IN_GROUPS = [
  '',
  'core',
  'batch',
  'apps',
  'extensions',
  'storage.k8s.io',
  'apiextensions.k8s.io',
  'apiregistration.k8s.io',
  'policy',
  'scheduling.k8s.io',
  'settings.k8s.io',
  'authorization.k8s.io',
  'authentication.k8s.io',
  'rbac.authorization.k8s.io',
  'certifcates.k8s.io',
  'networking.k8s.io',
];

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
    const manifests: IStageManifest[] = get(this.props.stage, ['context', 'outputs.manifests'], []).filter(m => !!m);
    const manifestIds = manifests.map(m => KubernetesManifestService.manifestIdentifier(m)).sort();
    if (prevState.manifestIds.join('') !== manifestIds.join('')) {
      this.unsubscribeAll();
      const subscriptions = manifests.map(manifest => {
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
    const params = {
      account: this.props.stage.context.account,
      name: this.scopedKind(manifest) + ' ' + manifest.metadata.name,
      location: manifest.metadata.namespace == null ? '_' : manifest.metadata.namespace,
    };
    return KubernetesManifestService.subscribe(this.props.application, params, (updated: IManifest) => {
      const idx = this.state.subscriptions.findIndex(sub => sub.id === id);
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

  private apiGroup(manifest: IStageManifest): string {
    const parts = (manifest.apiVersion || '_').split('/');
    if (parts.length < 2) {
      return '';
    }
    return parts[0];
  }

  private isCRDGroup(manifest: IStageManifest): boolean {
    return !BUILT_IN_GROUPS.includes(this.apiGroup(manifest));
  }

  private scopedKind(manifest: IStageManifest): string {
    if (this.isCRDGroup(manifest)) {
      return upperFirst(manifest.kind) + '.' + this.apiGroup(manifest);
    }

    return upperFirst(manifest.kind);
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
    const manifests: IManifest[] = this.state.subscriptions.filter(sub => !!sub.manifest).map(sub => sub.manifest);
    return (
      <ExecutionDetailsSection name={sectionName} current={currentSection}>
        <StageFailureMessage stage={stage} message={stage.failureMessage} />
        {manifests && (
          <div className="row">
            <div className="col-md-12">
              <div className="well alert alert-info">
                {manifests.map(manifest => {
                  const uid =
                    manifest.manifest.metadata.uid || KubernetesManifestService.manifestIdentifier(manifest.manifest);
                  return <ManifestStatus key={uid} manifest={manifest} stage={stage} />;
                })}
              </div>
            </div>
          </div>
        )}
      </ExecutionDetailsSection>
    );
  }
}
