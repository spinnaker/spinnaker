'use strict';

angular.module('deckApp')
  .factory('momentService', function($window) {
    return $window.moment;
  });
