'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.trigger', [
    require('./trigger.directive.js'),
    require('./triggers.directive.js'),
    require('./jenkins/jenkinsTrigger.module.js'),
    require('./pipeline/pipelineTrigger.module.js'),
    require('./cron/cronTrigger.module.js'),
    require('../stages/stage.module.js'),
  ]);
