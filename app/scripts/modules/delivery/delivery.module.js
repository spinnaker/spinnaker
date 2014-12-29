'use strict';

angular.module('deckApp.delivery', [
  'deckApp.pipelines',
  'deckApp.settings',
  'deckApp.utils.d3',
  'deckApp.utils.lodash',
  'deckApp.utils.rx',
  'deckApp.utils.scrollTo'
])
  .config(angular.noop)
  .run(angular.noop);
