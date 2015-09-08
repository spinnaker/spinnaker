'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup', [
    require('./details/resizeServerGroup.controller.js'),
    require('./serverGroup.write.service.js'),
    require('./serverGroup.transformer.js'),
    require('./configure/common/serverGroup.configure.common.module.js'),
    require('./pod/runningTasksTag.directive.js'),

  ]).name;
