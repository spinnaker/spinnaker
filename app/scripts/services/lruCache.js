'use strict';

angular.module('deckApp')
  .factory('lruCache', function($cacheFactory) {
    return $cacheFactory('lru', {number: 10});
  });
