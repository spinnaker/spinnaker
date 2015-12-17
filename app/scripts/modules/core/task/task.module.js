'use strict';

let angular = require('angular');

require('./tasks.less');

module.exports = angular
  .module('spinnaker.core.task', [
    require('./monitor/taskMonitor.module.js'),
    require('./tasks.api.js'),
    require('./tasks.controller.js'),
    require('./taskDetails.controller.js'),
    require('./task.read.service.js'),
    require('./task.write.service.js'),
    require('./statusGlyph.directive.js'),
  ]);
