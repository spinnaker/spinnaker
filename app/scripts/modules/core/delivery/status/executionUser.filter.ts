import { has } from 'lodash';
import { module } from 'angular';
import { Execution } from '../../domain'


export function executionUserFilter() {
  return function (input: Execution): string {
    if (!input.trigger.user) {
      return 'unknown user';
    }
    let user: string = input.trigger.user;
    if (user === '[anonymous]' && has(input, 'trigger.parentExecution.trigger.user')) {
      user = input.trigger.parentExecution.trigger.user;
    }
    return user;
  };
};

const MODULE_NAME = 'spinnaker.core.delivery.executionUser.filter';

module(MODULE_NAME, [])
  .filter('executionUser', executionUserFilter);

export default MODULE_NAME
