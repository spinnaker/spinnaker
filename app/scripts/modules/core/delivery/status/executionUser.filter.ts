import { has } from 'lodash';
import { module } from 'angular';

import { IExecution } from 'core/domain';

export function executionUserFilter() {
  return function (input: IExecution): string {
    if (!input.trigger.user) {
      return 'unknown user';
    }
    let user: string = input.trigger.user;
    if (user === '[anonymous]' && has(input, 'trigger.parentExecution.trigger.user')) {
      user = input.trigger.parentExecution.trigger.user;
    }
    return user;
  };
}

export const EXECUTION_USER_FILTER = 'spinnaker.core.delivery.executionUser.filter';
module(EXECUTION_USER_FILTER, [])
  .filter('executionUser', executionUserFilter);
