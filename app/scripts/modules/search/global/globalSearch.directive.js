'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.search.global')
  .directive('globalSearch', function($window, $) {
    return {
      restrict: 'E',
      replace: true,
      scope: {
      },
      templateUrl: require('../../../../views/globalsearch.html'),
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

        window.bind('keyup.globalsearch', function(event) {
          var $target = $(event.target);
          if ($target.is('input, textarea') || event.which !== 191) {
            return;
          }
          element.find('input').focus();
        });

        scope.$on('$destroy', function() {
          window.unbind('.globalsearch');
        });
      }
    };
  }).name;
