'use strict';


angular.module('deckApp')
  .directive('globalSearch', function($window, $) {
    return {
      restrict: 'E',
      replace: true,
      scope: {
      },
      templateUrl: 'views/globalsearch.html',
      controller: 'GlobalSearchCtrl as ctrl',
      link: function(scope, element) {
        var window = $($window);

        window.bind('click.globalsearch', function(event) {
          if (event.target === element.find('input').get(0)) {
            return;
          }
          scope.$apply(function(scope) {
            scope.showSearchResults = false;
          });
        });

        scope.$on('$destroy', function() {
          window.unbind('.globalsearch');
        });
      }
    };
  });
