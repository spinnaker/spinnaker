import { UISref, UISrefActive } from '@uirouter/react';
import React from 'react';

import { CloudProviderLogo } from '@spinnaker/core';

import { RawResourceUtils } from '../RawResourceUtils';

import './RawResource.less';

interface IRawResourceProps {
  resource: IApiKubernetesResource;
}

interface IRawResourceState {}

export class RawResource extends React.Component<IRawResourceProps, IRawResourceState> {
  constructor(props: IRawResourceProps) {
    super(props);
  }

  public render() {
    const { account, name, region } = this.props.resource;
    const params = { account, name, region };
    return (
      <UISrefActive class="active">
        <UISref to=".rawResourceDetails" params={params}>
          <div className="RawResource card clickable clickable-row">
            <h4 className="title">
              <CloudProviderLogo provider="kubernetes" height="20px" width="20px" />
              {this.props.resource.kind} {this.props.resource.displayName}
            </h4>
            <div className="details">
              <div className="column">
                <div className="title">account:</div>
                <div>{this.props.resource.account}</div>
              </div>
              <div className="column">
                <div className="title">namespace:</div>
                <div>{RawResourceUtils.namespaceDisplayName(this.props.resource.namespace)}</div>
              </div>
              <div className="column">
                <div className="title">apiVersion:</div>
                <div>{this.props.resource.apiVersion}</div>
              </div>
            </div>
          </div>
        </UISref>
      </UISrefActive>
    );
  }
}
