'use strict';

import { module } from 'angular';

export const ECS_SERVER_GROUP_LOGGING = 'spinnaker.ecs.serverGroup.configure.wizard.logging.component';
module(ECS_SERVER_GROUP_LOGGING, []).component('ecsServerGroupLogging', {
  bindings: {
    command: '=',
    application: '=',
  },
  templateUrl: require('./logging.component.html'),
});
