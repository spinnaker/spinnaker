'use strict';


angular.module('deckApp')
  .directive('instances', function (scrollTriggerService) {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/instance/instances.html',
      scope: {
        instances: '=',
        renderInstancesOnScroll: '=',
        highlight: '=',
        scrollTarget: '@',
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
          scrollTriggerService.register(scope, elem, scope.scrollTarget, showAllInstances);
        } else {
          showAllInstances();
        }
      }
    };
  }
);
