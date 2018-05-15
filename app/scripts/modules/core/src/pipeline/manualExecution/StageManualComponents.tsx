import * as React from 'react';

import { IPipelineCommand } from 'core/domain';
import { ITriggerTemplateComponentProps } from 'core/pipeline/manualExecution/TriggerTemplate';

/**
 * This is only necessary because manualPipelineExecution is still in angular
 * Once it is converted to React, this whole component can be removed
 */
export class StageManualComponents extends React.Component<{
  command: IPipelineCommand;
  components: Array<React.ComponentType<ITriggerTemplateComponentProps>>;
}> {
  public render() {
    return this.props.components.map((Comp, index) => <Comp key={index} command={this.props.command} />);
  }
}
