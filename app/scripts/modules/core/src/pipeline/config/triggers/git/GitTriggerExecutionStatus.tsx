import React from 'react';

import { IExecutionTriggerStatusComponentProps, IGitTrigger } from '../../../../domain';
import { Overridable } from '../../../../overrideRegistry';

@Overridable('git.trigger.executionStatus')
export class GitTriggerExecutionStatus extends React.Component<IExecutionTriggerStatusComponentProps> {
  public render() {
    const trigger = this.props.trigger as IGitTrigger;
    return (
      <>
        <li>
          {trigger.project}/{trigger.slug}
        </li>
        <li>Branch: {trigger.branch}</li>
      </>
    );
  }
}
