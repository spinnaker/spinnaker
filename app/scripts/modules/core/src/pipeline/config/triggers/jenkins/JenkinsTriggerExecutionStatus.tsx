import * as React from 'react';

import { IExecutionTriggerStatusComponentProps, IBuildTrigger } from 'core/domain';

export class JenkinsTriggerExecutionStatus extends React.Component<IExecutionTriggerStatusComponentProps> {
  public render() {
    const trigger = this.props.trigger as IBuildTrigger;
    return <li>{trigger.job}</li>;
  }
}
