'use strict';

angular.module('deckApp')
  .factory('$', function($window) {
    return $window.jQuery;
  });
