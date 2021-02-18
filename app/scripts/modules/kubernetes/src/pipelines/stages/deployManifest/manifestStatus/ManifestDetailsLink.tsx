import { get, trim } from 'lodash';
import React from 'react';

import { AccountService, IManifest, ReactInjector } from '@spinnaker/core';

const UNMAPPED_K8S_RESOURCE_STATE_KEY = 'kubernetesResource';

export interface IManifestDetailsProps {
  manifest: IManifest;
  linkName: string;
  accountId: string;
}

export interface IManifestDetailsState {
  url: string;
}

export class ManifestDetailsLink extends React.Component<IManifestDetailsProps, IManifestDetailsState> {
  private spinnakerKindStateMap: { [k: string]: string } = {
    // keys from clouddriver's KubernetesSpinnakerKindMap
    serverGroupManagers: 'serverGroupManager',
    serverGroups: 'serverGroup',
  };

  constructor(props: IManifestDetailsProps) {
    super(props);
    this.state = {
      url: '',
    };
    this.loadUrl();
  }

  private canOpen(): boolean {
    return !!this.props.manifest.manifest && !!this.state.url;
  }

  private spinnakerKindFromKubernetesKind(kind: string, kindMap: { [k: string]: string }) {
    const foundKind = Object.keys(kindMap).find((k) => k.toLowerCase() === kind.toLowerCase());
    return kindMap[foundKind];
  }

  private resourceRegion(): string {
    return trim(
      get(this.props, ['manifest', 'manifest', 'metadata', 'annotations', 'artifact.spinnaker.io/location'], ''),
    );
  }

  private getStateParams(stateKey: string): any {
    const kind = this.props.manifest.manifest.kind.toLowerCase();
    const name = this.props.manifest.manifest.metadata.name;
    const region = this.resourceRegion();
    const params: { [k: string]: string } = {
      accountId: this.props.accountId,
      provider: 'kubernetes',
      region,
      reg: region, // Filters the list of clusters on the Clusters screen to those in the same namespace
      [stateKey]: `${kind} ${name}`,
    };
    if (!params.region && kind === 'namespace' && stateKey === UNMAPPED_K8S_RESOURCE_STATE_KEY) {
      params.region = name;
    }
    if (!params.region || params.region === '') {
      params.region = '_';
    }
    return params;
  }

  private loadUrl() {
    const kind: string = get(this.props, ['manifest', 'manifest', 'kind'], '');
    const { accountId } = this.props;
    AccountService.getAccountDetails(accountId).then((account) => {
      const spinnakerKind = this.spinnakerKindFromKubernetesKind(kind, account.spinnakerKindMap);
      const stateKey = this.spinnakerKindStateMap[spinnakerKind] || UNMAPPED_K8S_RESOURCE_STATE_KEY;
      const params = this.getStateParams(stateKey);
      const url = ReactInjector.$state.href(`home.applications.application.insight.clusters.${stateKey}`, params);
      this.setState({ url });
    });
  }

  public render() {
    if (this.canOpen()) {
      return (
        <a href={this.state.url} className="clickable">
          {this.props.linkName}
        </a>
      );
    } else {
      return null;
    }
  }
}
