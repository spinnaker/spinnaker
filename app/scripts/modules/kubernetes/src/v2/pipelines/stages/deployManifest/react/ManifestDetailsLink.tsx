import * as React from 'react';
import { IManifest, Application, ReactInjector } from '@spinnaker/core';
import { trim } from 'lodash';

export interface IManifestDetailsProps {
  manifest: IManifest;
  linkName: string;
  application: Application;
  accountId: string;
}

const supportedKinds = ['deployment', 'replicaset'];

export class ManifestDetailsLink extends React.Component<IManifestDetailsProps> {
  constructor(props: IManifestDetailsProps) {
    super(props);
    this.openDetails = this.openDetails.bind(this);
  }

  private canOpen(): boolean {
    return !!(
      this.props.manifest.manifest &&
      this.props.manifest.manifest.kind &&
      this.props.manifest.manifest.metadata &&
      this.props.manifest.manifest.metadata.annotations &&
      supportedKinds.includes(this.props.manifest.manifest.kind.toLowerCase())
    );
  }

  public openDetails() {
    const kind = this.props.manifest.manifest.kind.toLowerCase();
    if (kind === 'deployment') {
      this.openDeploymentDetails();
    } else if (kind === 'replicaset') {
      this.openReplicaSetDetails();
    }
  }

  private buildParams(annotations: any): any {
    return {
      accountId: this.props.accountId,
      provider: 'kubernetes',
      application: annotations.application,
      region: annotations.region,
    };
  }

  private openDeploymentDetails() {
    const { $state } = ReactInjector;
    const annotations = this.extractAnnotations(this.props.manifest.manifest.metadata.annotations);
    const params = this.buildParams(annotations);
    params.serverGroupManager = `deployment ${annotations.name}`;
    $state.go('home.applications.application.insight.clusters.serverGroupManager', params);
  }

  private openReplicaSetDetails() {
    const { $state } = ReactInjector;
    const annotations = this.extractAnnotations(this.props.manifest.manifest.metadata.annotations);
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

  public render() {
    if (this.canOpen()) {
      return (
        <a onClick={this.openDetails} className="clickable">
          {this.props.linkName}
        </a>
      );
    } else {
      return null;
    }
  }
}
