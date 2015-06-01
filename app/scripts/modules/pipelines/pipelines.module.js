'use strict';

angular.module('spinnaker.pipelines', [
  'spinnaker.pipelines.config',
  'spinnaker.pipelines.config.controller',
  'spinnaker.pipelines.create.controller',
  'spinnaker.pipelines.config.validator.directive',
  'spinnaker.pipelines.dirtyTracker.service',
  'spinnaker.caches.viewStateCache',

  'restangular',
  'spinnaker.pipelines.stage',
  'spinnaker.pipelines.trigger',
  'spinnaker.pipelines.parameters',
  'spinnaker.pipelines.create',
  'spinnaker.pipelines.delete',
  'spinnaker.pipelines.enableParallel',
  'spinnaker.pipelines.disableParallel',
  'spinnaker.pipelines.rename',
  'spinnaker.pipelines.editJson',
  'spinnaker.authentication',
  'spinnaker.utils.lodash',
  'spinnaker.settings',
  'ui.sortable',

  'spinnaker.pipelines.graph.directive',
]);
