import { module } from 'angular';

import { SCALING_POLICY_MODULE } from './scalingPolicy/scalingPolicy.module';

export const SERVER_GROUP_DETAILS_MODULE = 'spinnaker.amazon.serverGroup.details';
module(SERVER_GROUP_DETAILS_MODULE, [
  SCALING_POLICY_MODULE,
  require('./securityGroup/editSecurityGroups.modal.controller').name,
  require('./scalingProcesses/modifyScalingProcesses.controller').name,
  require('./scheduledAction/editScheduledActions.modal.controller').name,
  require('./advancedSettings/editAsgAdvancedSettings.modal.controller').name,
  require('./rollback/rollbackServerGroup.controller').name,
]);
