'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('accountSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/accountSelectField.html',
      scope: {
        accounts: '=',
        component: '=',
        field: '@',
        loading: '=',
        onChange: '&'
      }
    };
  }
);
