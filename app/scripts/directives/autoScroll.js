'use strict';


angular.module('deckApp')
  .directive('autoScroll', function ($, $timeout) {
    return {
      restrict: 'A',
      link: function(scope, elem, attrs) {
        var $elem = $(elem);
        scope.$watch(attrs.autoScroll, function() {
          $timeout(function() {
            $elem.parent().scrollTop($elem.height());
          });
        }, true);
      }
    };
  }
);
