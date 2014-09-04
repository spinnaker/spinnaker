'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('securityGroup', function ($rootScope) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/application/connection/securityGroup.html',
      scope: {
        securityGroup: '=',
        displayOptions: '='
      },
      link: function (scope) {
        scope.$state = $rootScope.$state;
      }
    };
  }
);
