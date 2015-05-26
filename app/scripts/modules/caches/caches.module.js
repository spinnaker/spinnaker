'use strict';

angular
  .module('spinnaker.caches', [
    'spinnaker.caches.core',
    'spinnaker.caches.initializer',
    'spinnaker.caches.applicationLevelScheduled',
    'spinnaker.caches.collapsibleSectionState',
    'spinnaker.caches.infrastructure',
    'spinnaker.caches.scheduled'
  ]);
