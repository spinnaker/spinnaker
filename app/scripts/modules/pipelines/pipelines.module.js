'use strict';

angular.module('deckApp.pipelines', [
  'deckApp.pipelines.config',
  'deckApp.pipelines.config.controller',
  'deckApp.pipelines.create.controller',
  'deckApp.pipelines.config.validator.directive',
  'deckApp.pipelines.dirtyTracker.service',
  'deckApp.caches.viewStateCache',

  'restangular',
  'deckApp.pipelines.stage',
  'deckApp.pipelines.trigger',
  'deckApp.pipelines.create',
  'deckApp.pipelines.delete',
  'deckApp.pipelines.rename',
  'deckApp.pipelines.editJson',
  'deckApp.authentication',
  'deckApp.utils.lodash',
  'ui.sortable',
]);
