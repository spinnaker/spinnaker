import React from 'react';

import { CloudProviderRegistry } from './CloudProviderRegistry';

export interface ICloudProviderLabelProps {
  provider: string;
}

export interface ICloudProviderLabelState {
  label: string;
}

export class CloudProviderLabel extends React.Component<ICloudProviderLabelProps, ICloudProviderLabelState> {
  constructor(props: ICloudProviderLabelProps) {
    super(props);
    this.state = {
      label: this.getProviderLabel(props.provider),
    };
  }

  private getProviderLabel(provider: string): string {
    return CloudProviderRegistry.getValue(provider, 'name') || provider;
  }

  public componentWillReceiveProps(nextProps: ICloudProviderLabelProps): void {
    this.setState({
      label: this.getProviderLabel(nextProps.provider),
    });
  }

  public render() {
    return <span>{this.state.label}</span>;
  }
}
