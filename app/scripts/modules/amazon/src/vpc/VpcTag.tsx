import React from 'react';

import { VpcReader } from './VpcReader';

export interface IVpcTagProps {
  vpcId: string;
}

export interface IVpcTagState {
  label: string;
}

export class VpcTag extends React.Component<IVpcTagProps, IVpcTagState> {
  private defaultLabel = 'None (EC2 Classic)';

  constructor(props: IVpcTagProps) {
    super(props);
    this.state = { label: this.defaultLabel };
    this.updateState(props);
  }

  private updateState(props: IVpcTagProps): void {
    if (!props.vpcId) {
      this.setState({ label: this.defaultLabel });
    } else {
      VpcReader.getVpcName(props.vpcId).then((name) => {
        const label = name ? `${name} (${props.vpcId})` : `(${props.vpcId})`;
        this.setState({ label });
      });
    }
  }

  public componentWillReceiveProps(nextProps: IVpcTagProps): void {
    if (nextProps.vpcId !== this.props.vpcId) {
      this.updateState(nextProps);
    }
  }

  public render() {
    return <span className="vpc-tag">{this.state.label}</span>;
  }
}
