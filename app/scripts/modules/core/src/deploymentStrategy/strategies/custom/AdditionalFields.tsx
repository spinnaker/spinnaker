import React from 'react';

import { IDeploymentStrategyAdditionalFieldsProps } from '../../deploymentStrategy.registry';
import { PipelineSelector } from '../PipelineSelector';

export class AdditionalFields extends React.Component<IDeploymentStrategyAdditionalFieldsProps> {
  public render() {
    return <PipelineSelector command={this.props.command} type="strategies" />;
  }
}
