'use strict';

angular.module('deckApp')
  .factory('RxService', function($window) {
    return $window.Rx;
  });
