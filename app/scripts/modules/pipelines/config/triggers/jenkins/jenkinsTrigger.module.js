'use strict';

angular.module('spinnaker.pipelines.trigger.jenkins', [
  'spinnaker.pipelines.trigger',
  'restangular',
  'spinnaker.pipelines.trigger.jenkins.service',
  'spinnaker.caches.initializer',
  'spinnaker.caches.infrastructure',
  'spinnaker.utils.timeFormatters',
]);
