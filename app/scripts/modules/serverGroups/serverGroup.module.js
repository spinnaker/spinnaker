'use strict';

angular
  .module('spinnaker.serverGroup', [
    'spinnaker.closable.modal.controller',
    'spinnaker.scalingActivities.controller',
    'spinnaker.resizeServerGroup.controller',

    'spinnaker.serverGroup.write.service',
    'spinnaker.serverGroup.transformer.service',
    'spinnaker.serverGroup.configure.aws',
    'spinnaker.serverGroup.configure.gce',
    'spinnaker.serverGroup.configure.common',
    'spinnaker.serverGroup.display.tasks.tag',
    'spinnaker.serverGroup.details.aws',

    'spinnaker.serverGroup.details.gce.controller',
    'spinnaker.aws.cloneServerGroup.controller'
  ]);
