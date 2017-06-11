import { module } from 'angular';

import { SCALING_POLICY_MODULE } from './scalingPolicy/scalingPolicy.module';

export const SERVER_GROUP_DETAILS_MODULE = 'spinnaker.amazon.serverGroup.details';
module(SERVER_GROUP_DETAILS_MODULE, [
  SCALING_POLICY_MODULE,
  require('./serverGroupDetails.aws.controller.js'),
  require('./scalingProcesses/autoScalingProcess.service.js'),
  require('./scalingProcesses/modifyScalingProcesses.controller.js'),
  require('./scheduledAction/editScheduledActions.modal.controller.js'),
  require('./advancedSettings/editAsgAdvancedSettings.modal.controller.js'),
]);
