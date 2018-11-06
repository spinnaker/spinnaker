import * as React from 'react';

import { IDeploymentStrategyAdditionalFieldsProps } from 'core/deploymentStrategy/deploymentStrategy.registry';
import { PipelineSelector } from '../PipelineSelector';

export class AdditionalFields extends React.Component<IDeploymentStrategyAdditionalFieldsProps> {
  public render() {
    return <PipelineSelector command={this.props.command} type="strategies" />;
  }
}
