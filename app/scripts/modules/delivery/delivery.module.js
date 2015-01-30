'use strict';

angular.module('deckApp.delivery', [
  'deckApp.pipelines',
  'deckApp.settings',
  'deckApp.utils.appendTransform',
  'deckApp.utils.d3',
  'deckApp.utils.lodash',
  'deckApp.utils.moment',
  'deckApp.utils.rx',
  'deckApp.utils.scrollTo',
  'deckApp.orchestratedItem.service',
])
  .config(angular.noop)
  .run(angular.noop);
