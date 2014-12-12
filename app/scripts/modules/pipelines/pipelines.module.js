'use strict';

angular.module('deckApp.pipelines', [
  'restangular',
  'deckApp.pipelines.stage',
  'deckApp.pipelines.trigger',
  'deckApp.pipelines.create',
  'deckApp.pipelines.delete',
  'deckApp.pipelines.rename',
  'ui.sortable',
]);
