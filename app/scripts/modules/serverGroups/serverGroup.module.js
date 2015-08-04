'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup', [
    require('./details/closeable.modal.controller.js'),
    require('./details/scalingActivities.controller.js'),
    require('./details/resizeServerGroup.controller.js'),

    require('./serverGroup.write.service.js'),
    require('./configure/common/transformer/serverGroup.transformer.service.js'),
    require('./configure/common/serverGroup.configure.common.module.js'),
    require('./pod/runningTasksTag.directive.js'),

  ]).name;
