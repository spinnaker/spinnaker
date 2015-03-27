'use strict';

angular
  .module('deckApp.caches', [
    'deckApp.caches.core',
    'deckApp.caches.initializer',
    'deckApp.caches.applicationLevelScheduled',
    'deckApp.caches.collapsibleSectionState',
    'deckApp.caches.infrastructure',
    'deckApp.caches.scheduled'
  ]);
