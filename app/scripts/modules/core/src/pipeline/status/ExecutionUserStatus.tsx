import React from 'react';

import { IExecutionTriggerStatusComponentProps } from 'core/domain';

export class ExecutionUserStatus extends React.Component<IExecutionTriggerStatusComponentProps> {
  public render() {
    const { authentication, trigger } = this.props;
    const authenticatedUser = authentication.user ?? '[anonymous]';
    const triggerUser = trigger.user ?? 'unknown user';
    return triggerUser === authenticatedUser ? authenticatedUser : `${triggerUser} (${authenticatedUser})`;
  }
}
