'use strict';

angular.module('spinnaker.pipelines.stage.pipeline', [
  'spinnaker.pipelines.stage',
  'spinnaker.pipelines.stage.core',
  'spinnaker.caches.initializer',
  'spinnaker.caches.infrastructure',
  'spinnaker.utils.timeFormatters',
  'spinnaker.pipelines.config.service',
  'spinnaker.pipelines.stage.pipeline.executionDetails.controller',
]);
