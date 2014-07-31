'use strict';

angular.module('deckApp')
  .directive('serverGroup', function() {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/application/cluster/serverGroup.html',
      scope: {
        cluster: '=',
        serverGroup: '='
      },
      link: function(scope) {
        scope.$state = scope.$parent.$state;
      }
    };
  });
