import * as React from 'react';
import { has } from 'lodash';

import { IExecutionTriggerStatusComponentProps, IPipelineTrigger } from 'core/domain';

export class ExecutionUserStatus extends React.Component<IExecutionTriggerStatusComponentProps> {
  public render() {
    const { trigger } = this.props;
    if (!trigger.user) {
      return 'unknown user';
    }
    let user: string = trigger.user;
    if (user === '[anonymous]' && has(trigger, 'parentExecution.trigger.user')) {
      user = (trigger as IPipelineTrigger).parentExecution.trigger.user;
    }
    return user;
  }
}
