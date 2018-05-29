import * as React from 'react';
import { get, upperFirst } from 'lodash';
import {
  IExecutionDetailsSectionProps,
  ExecutionDetailsSection,
  StageFailureMessage,
  IManifest,
} from '@spinnaker/core';
import { KubernetesManifestService } from '../../../../manifest/manifest.service';
import { ManifestStatus } from './ManifestStatus';

interface IManifestSubscription {
  id: string;
  unsubscribe: () => void;
  manifest: IManifest;
}

interface IStageManifest {
  kind: string;
  metadata: {
    namespace: string;
    name: string;
  };
}

interface IDeployStatusState {
  subscriptions: IManifestSubscription[];
}

export class DeployStatus extends React.Component<IExecutionDetailsSectionProps, IDeployStatusState> {
  public static title = 'deployStatus';

  constructor(props: IExecutionDetailsSectionProps) {
    super(props);
    this.state = { subscriptions: [] };
  }

  public componentDidMount() {
    this.buildSubscriptions();
  }

  public componentDidUpdate() {
    this.buildSubscriptions();
  }

  private buildSubscriptions() {
    const subscriptions: IManifestSubscription[] = [];
    const stageManifests = this.getStageManifests();
    stageManifests.forEach(m => {
      const subscription = this.subscribeToManifest(m);
      if (subscription != null) {
        subscriptions.push(subscription);
      }
    });
    if (subscriptions.length > 0) {
      this.setState({ subscriptions });
    }
  }

  public componentWillUnmount() {
    this.state.subscriptions.forEach(({ unsubscribe }) => unsubscribe());
  }

  private manifestIdentifier(manifest: IStageManifest) {
    const kind = manifest.kind.toLowerCase();
    const namespace = manifest.metadata.namespace.toLowerCase();
    const name = manifest.metadata.name.toLowerCase();
    return `${namespace} ${kind} ${name}`;
  }

  private findSubscriptionIndex({ id }: { id: string }): number {
    return this.state.subscriptions.findIndex(sub => sub.id === id);
  }

  private manifestFullName(manifest: any): string {
    return upperFirst(manifest.kind) + ' ' + manifest.metadata.name;
  }

  private getStageManifests(): IStageManifest[] {
    const manifests: any[] = get(this.props, ['stage', 'context', 'outputs.manifests'], []);
    return manifests.filter(m => !!m);
  }

  private subscribeToManifest(manifest: IStageManifest): IManifestSubscription {
    const { application, stage } = this.props;
    const { account } = stage.context;
    const { namespace: location } = manifest.metadata;
    const name = this.manifestFullName(manifest);
    const params = { account, location, name };
    const id = this.manifestIdentifier(manifest);
    if (this.findSubscriptionIndex({ id }) !== -1) {
      return null;
    }
    const unsubscribe = KubernetesManifestService.subscribe(application, params, (updatedManifest: IManifest) => {
      this.saveManifestSubscription({ id, unsubscribe, manifest: updatedManifest });
    });
    return { id, unsubscribe, manifest: null };
  }

  private saveManifestSubscription(subscription: IManifestSubscription) {
    const idx = this.findSubscriptionIndex(subscription);
    const subscriptions = [...this.state.subscriptions];
    if (idx === -1) {
      subscriptions.push(subscription);
    } else {
      subscriptions[idx] = subscription;
    }
    this.setState({ subscriptions });
  }

  public render() {
    const { name: sectionName, current: currentSection, application, stage } = this.props;
    const manifests: IManifest[] = this.state.subscriptions.filter(sub => !!sub.manifest).map(sub => sub.manifest);
    return (
      <ExecutionDetailsSection name={sectionName} current={currentSection}>
        <StageFailureMessage stage={stage} message={stage.failureMessage} />
        {manifests && (
          <div className="row">
            <div className="col-md-12">
              <div className="well alert alert-info">
                {manifests.map(manifest => {
                  const uid = manifest.manifest.metadata.uid;
                  return <ManifestStatus key={uid} manifest={manifest} application={application} stage={stage} />;
                })}
              </div>
            </div>
          </div>
        )}
      </ExecutionDetailsSection>
    );
  }
}
