'use strict';

angular.module('spinnaker.pipelines.trigger.pipeline', [
  'spinnaker.pipelines.trigger',
  'restangular',
  'spinnaker.pipelines.trigger.pipeline.service',
  'spinnaker.caches.initializer',
  'spinnaker.caches.infrastructure',
  'spinnaker.utils.timeFormatters',
]);
