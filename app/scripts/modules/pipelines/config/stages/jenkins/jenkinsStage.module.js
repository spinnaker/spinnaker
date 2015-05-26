'use strict';

angular.module('spinnaker.pipelines.stage.jenkins', [
  'spinnaker.pipelines.stage',
  'spinnaker.pipelines.stage.core',
  'spinnaker.caches.initializer',
  'spinnaker.caches.infrastructure',
  'spinnaker.utils.timeFormatters',
  'spinnaker.pipelines.trigger.jenkins.service',
  'spinnaker.pipelines.stage.jenkins.executionDetails.controller',
]);
