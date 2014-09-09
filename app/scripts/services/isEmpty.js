'use strict';


angular.module('deckApp')
  .factory('isEmpty', function () {
    return function (value) {
      return angular.isUndefined(value) || value === '' || value === null || value !== value;
    };
  }
);
