'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.tasks', [
    require('./monitor/taskMonitor.module.js'),
    require('./tasks.api.config.js'),
    require('./tasks.controller.js'),
    require('./taskDetails.controller.js'),
    require('../tasks/tasks.read.service.js'),
    require('../tasks/tasks.write.service.js'),
    require('./statusGlyph.directive.js'),
  ]).name;
