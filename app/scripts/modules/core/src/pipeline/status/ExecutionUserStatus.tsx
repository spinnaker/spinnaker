import * as React from 'react';
import { has } from 'lodash';

import { IExecutionTriggerStatusComponentProps, IPipelineTrigger, ITrigger, IExecution } from 'core/domain';
import { IAuthentication } from 'core/domain/IAuthentication';

export class ExecutionUserStatus extends React.Component<IExecutionTriggerStatusComponentProps> {
  public render() {
    const { authentication, trigger } = this.props;
    const parentExecution = has(trigger, 'parentExecution') ? (trigger as IPipelineTrigger).parentExecution : null;
    const authenticatedUser = getAuthenticatedUser(authentication, parentExecution);
    const triggerUser = getTriggerUser(trigger);
    return triggerUser === authenticatedUser ? authenticatedUser : `${triggerUser} (${authenticatedUser})`;
  }
}

const getTriggerUser = (trigger: ITrigger): string => {
  if (!trigger.user) {
    return 'unknown user';
  }
  let user: string = trigger.user;
  if (user === '[anonymous]' && has(trigger, 'parentExecution.trigger.user')) {
    user = (trigger as IPipelineTrigger).parentExecution.trigger.user;
  }
  return user;
};

const getAuthenticatedUser = (authentication: IAuthentication, parentExecution?: IExecution): string => {
  const user: string = authentication.user;
  if (user && user !== '[anonymous]') {
    return user;
  } else {
    if (has(parentExecution, 'authentication.user')) {
      return parentExecution.authentication.user;
    } else {
      return 'unknown user';
    }
  }
};
