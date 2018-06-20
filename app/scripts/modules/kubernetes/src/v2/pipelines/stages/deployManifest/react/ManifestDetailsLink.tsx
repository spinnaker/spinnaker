import * as React from 'react';
import { IManifest, Application, ReactInjector, AccountService } from '@spinnaker/core';
import { get, trim } from 'lodash';

export interface IManifestDetailsProps {
  manifest: IManifest;
  linkName: string;
  application: Application;
  accountId: string;
}

export class ManifestDetailsLink extends React.Component<IManifestDetailsProps> {
  private spinnakerKindStateMap: { [k: string]: string } = {
    // keys from clouddriver's KubernetesSpinnakerKindMap
    serverGroupManagers: 'serverGroupManager',
    serverGroups: 'serverGroup',
  };

  constructor(props: IManifestDetailsProps) {
    super(props);
    this.onClick = this.onClick.bind(this);
  }

  private canOpen(): boolean {
    return !!this.props.manifest.manifest;
  }

  private spinnakerKindFromKubernetesKind(kind: string, kindMap: { [k: string]: string }) {
    const foundKind = Object.keys(kindMap).find(k => k.toLowerCase() === kind.toLowerCase());
    return kindMap[foundKind];
  }

  private resourceRegion(): string {
    return trim(
      get(this.props, ['manifest', 'manifest', 'metadata', 'annotations', 'artifact.spinnaker.io/location'], ''),
    );
  }

  private openDetails(stateKey: string) {
    const { $state } = ReactInjector;
    const region = this.resourceRegion();
    const params: { [k: string]: string } = { accountId: this.props.accountId, provider: 'kubernetes', region };
    const kind = this.props.manifest.manifest.kind.toLowerCase();
    params[stateKey] = `${kind} ${this.props.manifest.manifest.metadata.name}`;
    $state.go(`home.applications.application.insight.clusters.${stateKey}`, params);
  }

  public onClick() {
    const kind: string = get(this.props, ['manifest', 'manifest', 'kind'], '');
    const { accountId } = this.props;
    AccountService.getAccountDetails(accountId).then(account => {
      const spinnakerKind = this.spinnakerKindFromKubernetesKind(kind, account.spinnakerKindMap);
      const stateKey = this.spinnakerKindStateMap[spinnakerKind] || 'kubernetesResource';
      this.openDetails(stateKey);
    });
  }

  public render() {
    if (this.canOpen()) {
      return (
        <a onClick={this.onClick} className="clickable">
          {this.props.linkName}
        </a>
      );
    } else {
      return null;
    }
  }
}
