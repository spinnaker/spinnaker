'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.serverGroup', [
    require('./serverGroup.transformer.js'),
    require('./configure/common/serverGroup.configure.common.module.js'),
    require('./pod/runningTasksTag.directive.js'),
    require('./details/multipleServerGroups.controller.js'),
    require('./serverGroup.dataSource')
  ]);
