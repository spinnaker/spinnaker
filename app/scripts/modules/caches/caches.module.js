'use strict';

angular
  .module('deckApp.caches', [
    'deckApp.caches.initializer',
    'deckApp.caches.applicationLevelScheduled',
    'deckApp.caches.collapsibleSectionState',
    'deckApp.caches.infrastructure',
    'deckApp.caches.scheduled'
  ]);