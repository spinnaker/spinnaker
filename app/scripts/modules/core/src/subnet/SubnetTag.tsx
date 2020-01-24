import React from 'react';
import { SubnetReader } from './subnet.read.service';

export interface ISubnetTagProps {
  subnetId?: string;
}

export interface ISubnetTagState {
  subnetLabel?: string;
}

export class SubnetTag extends React.Component<ISubnetTagProps, ISubnetTagState> {
  public state: ISubnetTagState = { subnetLabel: this.props.subnetId ? `(${this.props.subnetId})` : null };

  public componentDidMount() {
    const { subnetId } = this.props;
    if (subnetId) {
      SubnetReader.getSubnetPurpose(subnetId).then(name => {
        this.setState({
          subnetLabel: name,
        });
      });
    }
  }

  public render() {
    return <span className="subnet-tag">{this.state.subnetLabel}</span>;
  }
}
