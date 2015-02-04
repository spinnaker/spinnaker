'use strict';

angular.module('deckApp.pipelines', [
  'deckApp.pipelines.config',
  'deckApp.pipelines.config.controller',
  'deckApp.pipelines.create.controller',


  'restangular',
  'deckApp.pipelines.stage',
  'deckApp.pipelines.trigger',
  'deckApp.pipelines.create',
  'deckApp.pipelines.delete',
  'deckApp.pipelines.rename',
  'deckApp.authentication',
  'deckApp.utils.lodash',
  'ui.sortable',
]);
