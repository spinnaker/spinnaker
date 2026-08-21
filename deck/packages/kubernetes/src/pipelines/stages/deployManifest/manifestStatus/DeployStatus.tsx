import { get } from 'lodash';
import React from 'react';

import type { IExecutionDetailsSectionProps, IManifest } from '@spinnaker/core';
import { CollapsibleElement, ExecutionDetailsSection, ManifestYaml, SETTINGS, StageFailureMessage } from '@spinnaker/core';

import { ManifestStatus } from './ManifestStatus';
import type { IStageManifest, IStoredManifestReference } from '../../../../manifest/manifest.service';
import { isStoredManifestReference, KubernetesManifestService } from '../../../../manifest/manifest.service';

import './DeployStatus.less';

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
    const manifests: IStageManifest[] = get(this.props.stage, ['context', 'outputs.manifests'], [])
      .filter((m) => !!m)
      .filter((m) => !isStoredManifestReference(m));
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

  // Manifests the artifact-store entity storage feature replaced with a reference instead
  // of expanding them before deck saw them. We can't determine their kind/name/namespace
  // (and so can't subscribe to live status for them) without fetching the content, so we
  // let the user load them on demand instead, same as a baked manifest's YAML.
  private get storedManifestRefs(): IStoredManifestReference[] {
    return get(this.props.stage, ['context', 'outputs.manifests'], [])
      .filter((m: unknown) => !!m)
      .filter(isStoredManifestReference);
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
      <div className="deploy-status">
        <ExecutionDetailsSection name={sectionName} current={currentSection}>
          {SETTINGS.feature.multiBlockFailureMessages ? (
            stage.failureMessages.map((failureMessage) => (
              <CollapsibleElement key={failureMessage} maxHeight={150}>
                <StageFailureMessage stage={stage} message={failureMessage} />
              </CollapsibleElement>
            ))
          ) : (
            <StageFailureMessage stage={stage} message={stage.failureMessage} />
          )}
          {!!manifests?.length && (
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
          {!!this.storedManifestRefs.length && (
            <div className="row">
              <div className="col-md-12">
                <div className="well alert alert-info">
                  {this.storedManifestRefs.map((ref, i) => (
                    <div key={i}>
                      <ManifestYaml
                        linkName="Load Manifest Details"
                        modalTitle="Manifest"
                        manifestUri={ref.reference.replace(/^ref?:\/\//, '')}
                      />
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </ExecutionDetailsSection>
      </div>
    );
  }
}
