import React from 'react';

import { IBuildTrigger, IExecutionTriggerStatusComponentProps } from '../../../../domain';

export class JenkinsTriggerExecutionStatus extends React.Component<IExecutionTriggerStatusComponentProps> {
  public render() {
    const trigger = this.props.trigger as IBuildTrigger;
    return <li>{trigger.job}</li>;
  }
}
