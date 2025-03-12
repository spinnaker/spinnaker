import React from 'react';

import type { ITriggersProps } from './Triggers';
import { Triggers } from './Triggers';
import type { IPipeline } from '../../../domain';

export class TriggersWrapper extends React.Component<ITriggersProps> {
  private updatePipelineConfig = (changes: Partial<IPipeline>): void => {
    this.props.updatePipelineConfig(changes);
    this.forceUpdate();
  };

  public render() {
    return <Triggers {...this.props} updatePipelineConfig={this.updatePipelineConfig} />;
  }
}
