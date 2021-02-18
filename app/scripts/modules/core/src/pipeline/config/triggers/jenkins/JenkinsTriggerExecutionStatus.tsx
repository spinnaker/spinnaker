import { IBuildTrigger, IExecutionTriggerStatusComponentProps } from 'core/domain';
import React from 'react';

export class JenkinsTriggerExecutionStatus extends React.Component<IExecutionTriggerStatusComponentProps> {
  public render() {
    const trigger = this.props.trigger as IBuildTrigger;
    return <li>{trigger.job}</li>;
  }
}
