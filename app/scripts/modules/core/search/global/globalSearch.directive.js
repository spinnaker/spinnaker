'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.search.global.directive', [
  require('./globalSearch.controller.js')
])
  .directive('globalSearch', function($window, $) {
    return {
      restrict: 'E',
      replace: true,
      scope: {
      },
      templateUrl: require('./globalSearch.directive.html'),
      controller: 'GlobalSearchCtrl as ctrl',
      link: function(scope, element) {
        const slashKey = 191;
        var window = $($window);

        window.bind('click.globalsearch', function(event) {
          if (event.target === element.find('input').get(0)) {
            return;
          }

          scope.showSearchResults = false;
          scope.showRecentItems = false;
          scope.$digest();
        });

        var isQuestionMark = (event) => {
          return event.which === slashKey && event.shiftKey;
        };

        window.bind('keyup.globalsearch', function(event) {
          var $target = $(event.target);
          if ($target.is('input, textarea') || isQuestionMark(event) || event.which !== slashKey) {
            return;
          }
          element.find('input').focus();
        });

        scope.$on('$destroy', function() {
          window.unbind('.globalsearch');
        });
      }
    };
  });
