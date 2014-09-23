'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('regionSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/regionSelectField.html',
      scope: {
        regions: '=',
        component: '=',
        field: '@',
        account: '=',
        onChange: '&'
      }
    };
  }
);
