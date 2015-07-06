'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.config.trigger', [
  require('./trigger.directive.js'),
  require('./triggers.directive.js'),
  require('./jenkins/jenkinsTrigger.module.js'),
  require('./pipeline/pipelineTrigger.module.js'),
  require('../stages/stage.module.js'),
]).name;
