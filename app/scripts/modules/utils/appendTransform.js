'use strict';

angular.module('deckApp.utils.appendTransform')
  .factory('appendTransform', function($http) {
    function append(defaults, transform) {
      defaults = angular.isArray(defaults) ? defaults : [defaults];
      return defaults.concat(transform);
    }

    return function(transform) {
      return append($http.defaults.transformResponse, transform);
    };

  });
