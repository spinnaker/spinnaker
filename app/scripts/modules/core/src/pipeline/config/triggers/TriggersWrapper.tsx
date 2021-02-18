import { IPipeline } from 'core/domain';
import React from 'react';

import { ITriggersProps, Triggers } from './Triggers';

export class TriggersWrapper extends React.Component<ITriggersProps> {
  private updatePipelineConfig = (changes: Partial<IPipeline>): void => {
    this.props.updatePipelineConfig(changes);
    this.forceUpdate();
  };

  public render() {
    return <Triggers {...this.props} updatePipelineConfig={this.updatePipelineConfig} />;
  }
}
