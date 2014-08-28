'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('instances', function (scrollTriggerService) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/application/instances.html',
      scope: {
        instances: '=',
        renderInstancesOnScroll: '=',
        highlight: '='
      },
      link: function (scope, elem) {
        scope.$state = scope.$parent.$state;
        scope.rendered = false;

        function showAllInstances() {
          scope.$evalAsync(function() {
            scope.displayedInstances = scope.instances;
            scope.rendered = true;
          });
        }

        if (scope.renderInstancesOnScroll) {
          scope.displayedInstances = [];
          scrollTriggerService.register(scope, elem, showAllInstances);
        } else {
          showAllInstances();
        }
      }
    };
  }
);
