'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('validationError', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/validationError.html',
      scope: {
        message: '@'
      }
    };
  }
);
