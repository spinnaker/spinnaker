'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('healthCounts', function () {
    return {
      templateUrl: 'views/application/healthCounts.html',
      restrict: 'E',
      replace: true,
      scope: {
        container: '='
      }
    };
  }
);
