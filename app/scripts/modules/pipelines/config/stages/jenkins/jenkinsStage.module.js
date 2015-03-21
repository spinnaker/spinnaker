'use strict';

angular.module('deckApp.pipelines.stage.jenkins', [
  'deckApp.pipelines.stage',
  'deckApp.pipelines.stage.core',
  'deckApp.caches.initializer',
  'deckApp.caches.infrastructure',
  'deckApp.utils.timeFormatters',
  'deckApp.pipelines.trigger.jenkins.service',
]);
