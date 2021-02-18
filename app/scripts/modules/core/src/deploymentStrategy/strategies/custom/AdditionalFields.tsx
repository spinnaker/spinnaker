import React from 'react';

import { PipelineSelector } from '../PipelineSelector';
import { IDeploymentStrategyAdditionalFieldsProps } from '../../deploymentStrategy.registry';

export class AdditionalFields extends React.Component<IDeploymentStrategyAdditionalFieldsProps> {
  public render() {
    return <PipelineSelector command={this.props.command} type="strategies" />;
  }
}
