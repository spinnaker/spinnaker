'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup', [
    require('./details/closeable.modal.controller.js'),
    require('./details/scalingActivities.controller.js'),
    require('./details/resizeServerGroup.controller.js'),

    require('./serverGroup.write.service.js'),
    require('./configure/common/transformer/serverGroup.transformer.service.module.js'),
    require('./configure/aws/serverGroup.configure.aws.module.js'),
    require('./configure/gce/serverGroup.configure.gce.module.js'),
    require('./configure/common/serverGroup.configure.common.module.js'),
    require('./pod/runningTasksTag.directive.js'),
    require('./details/aws/serverGroup.details.module.js'),

    require('./details/gce/serverGroupDetails.gce.controller.js'),
    require('./configure/aws/wizard/CloneServerGroup.aws.controller.js')
  ]);
