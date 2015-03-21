'use strict';

angular.module('deckApp.pipelines.trigger.jenkins', [
  'deckApp.pipelines.trigger',
  'restangular',
  'deckApp.pipelines.trigger.jenkins.service',
  'deckApp.caches.initializer',
  'deckApp.caches.infrastructure',
  'deckApp.utils.timeFormatters',
]);
