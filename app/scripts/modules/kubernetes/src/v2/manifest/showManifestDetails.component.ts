import { IComponentOptions, IController, IScope, module } from 'angular';
import { trim } from 'lodash';

import { Application, IManifest, ReactInjector } from '@spinnaker/core';
import { KubernetesManifestService } from './manifest.service';

const supportedKinds = ['deployment', 'replicaset'];

class KubernetesShowManifestDetails implements IController {
  public manifestContents: any;
  public manifest: IManifest;
  public accountId: string;
  public application: Application;

  public constructor(kubernetesManifestService: KubernetesManifestService, private $scope: IScope) {
    kubernetesManifestService.makeManifestRefresher(
      this.application,
      this.$scope,
      {
        account: this.accountId,
        location: this.manifestContents.metadata.namespace,
        name: this.manifestFullName(),
      },
      this,
    );
  }

  public canOpen(): boolean {
    return !!(
      this.manifestContents &&
      this.manifestContents.kind &&
      this.manifestContents.metadata &&
      this.manifestContents.metadata.annotations &&
      supportedKinds.includes(this.manifestContents.kind.toLowerCase())
    );
  }

  public openDetails() {
    const kind = this.manifestContents.kind.toLowerCase();
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
    const annotations = this.extractAnnotations(this.manifestContents.metadata.annotations);
    const params = this.buildParams(annotations);
    params.serverGroupManager = `deployment ${annotations.name}`;
    $state.go('home.applications.application.insight.clusters.serverGroupManager', params);
  }

  private openReplicaSetDetails() {
    const { $state } = ReactInjector;
    const annotations = this.extractAnnotations(this.manifestContents.metadata.annotations);
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

  private manifestFullName(): string {
    return this.normalizeKind(this.manifestContents.kind) + ' ' + this.manifestContents.metadata.name;
  }

  private normalizeKind(kind: string): string {
    return kind.charAt(0).toUpperCase() + kind.slice(1);
  }
}

class KubernetesShowManifestDetailsComponent implements IComponentOptions {
  public bindings: any = { manifestContents: '<', linkName: '<', accountId: '<', application: '=' };
  public controller: any = KubernetesShowManifestDetails;
  public controllerAs = 'ctrl';
  public template = trim(`
    <a href ng-if='ctrl.canOpen()' ng-click='ctrl.openDetails()'>{{ctrl.linkName}}</a>
    <kubernetes-manifest-events manifest="ctrl.manifest"></kubernetes-manifest-events>
  `);
}

export const KUBERNETES_SHOW_MANIFEST_DETAILS = 'spinnaker.kubernetes.v2.manifest.showDetails';
module(KUBERNETES_SHOW_MANIFEST_DETAILS, []).component(
  'kubernetesShowManifestDetails',
  new KubernetesShowManifestDetailsComponent(),
);
