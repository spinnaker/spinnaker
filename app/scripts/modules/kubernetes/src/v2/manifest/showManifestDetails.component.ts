import { module, IComponentOptions, IController } from 'angular';
import { trim } from 'lodash';
import { ReactInjector } from '@spinnaker/core';

const supportedKinds = ['deployment', 'replicaset'];

class KubernetesShowManifestDetails implements IController {
  public manifest: any;
  public accountId: string;

  public canOpen(): boolean {
    return !!(
      this.manifest &&
      this.manifest.kind &&
      this.manifest.metadata &&
      this.manifest.metadata.annotations &&
      supportedKinds.includes(this.manifest.kind.toLowerCase())
    );
  }

  public openDetails() {
    const kind = this.manifest.kind.toLowerCase();
    if (kind === 'deployment') {
      this.openDeploymentDetails();
    } else if (kind === 'replicaset') {
      this.openReplicaSetDetails();
    }
  }

  private buildParams(annotations: any): any {
    return {
      accountId: this.accountId,
      provider: 'kubernetes',
      application: annotations.application,
      region: annotations.region,
    };
  }

  private openDeploymentDetails() {
    const { $state } = ReactInjector;
    const annotations = this.extractAnnotations(this.manifest.metadata.annotations);
    const params = this.buildParams(annotations);
    params.serverGroupManager = `deployment ${annotations.name}`;
    $state.go('home.applications.application.insight.clusters.serverGroupManager', params);
  }

  private openReplicaSetDetails() {
    const { $state } = ReactInjector;
    const annotations = this.extractAnnotations(this.manifest.metadata.annotations);
    const params = this.buildParams(annotations);
    params.serverGroup = `replicaSet ${annotations.name}-${annotations.version}`;
    $state.go('home.applications.application.insight.clusters.serverGroup', params);
  }

  private stripQuotes(str: string): string {
    return trim(str, '"');
  }

  private extractAnnotations(annotations?: any): any {
    return {
      application: this.stripQuotes(annotations['moniker.spinnaker.io/application']),
      region: this.stripQuotes(annotations['artifact.spinnaker.io/location']),
      name: this.stripQuotes(annotations['artifact.spinnaker.io/name']),
      version: this.stripQuotes(annotations['artifact.spinnaker.io/version']),
    };
  }
}

class KubernetesShowManifestDetailsComponent implements IComponentOptions {
  public bindings: any = { manifest: '<', linkName: '<', accountId: '<' };
  public controller: any = KubernetesShowManifestDetails;
  public controllerAs = 'ctrl';
  public template = trim(`
    <a href ng-if='ctrl.canOpen()' ng-click='ctrl.openDetails()'>{{ctrl.linkName}}</a>
  `);
}

export const KUBERNETES_SHOW_MANIFEST_DETAILS = 'spinnaker.kubernetes.v2.manifest.showDetails';
module(KUBERNETES_SHOW_MANIFEST_DETAILS, [])
  .component('kubernetesShowManifestDetails', new KubernetesShowManifestDetailsComponent());
