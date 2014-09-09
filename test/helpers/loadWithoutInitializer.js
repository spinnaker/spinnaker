/* jshint unused: false */
'use strict';

var loadDeckWithoutCacheInitializer = function() {
  module('deckApp', function($provide) {
    return $provide.decorator('cacheInitializer', function() {
      return {
        initialize: angular.noop,

      };
    });
  });
};
