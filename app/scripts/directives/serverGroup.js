'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('serverGroup', function ($rootScope) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/application/cluster/serverGroup.html',
      scope: {
        cluster: '=',
        serverGroup: '=',
        displayOptions: '='
      },
      link: function (scope) {
        scope.$state = $rootScope.$state;
      }
    };
  }
);
