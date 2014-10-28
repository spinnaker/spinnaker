'use strict';

angular.module('deckApp')
  .directive('timeline', function($templateCache) {
    return {
      restrict: 'E',
      replace: true,
      compile: function(tElement, tAttrs) {
        var inner = tElement.children();
        tElement.html($templateCache
          .get('views/timeline.html')
          .replace('element', tAttrs.element)
          .replace('iterable', tAttrs.iterable));
        tElement.find('.interior').append(inner);
        return function(scope, iElem, iAttrs) {
          scope.timestampGetter = iAttrs.timestamp;
          scope.iterableName = iAttrs.iterable;
        };
      },
      controller: 'TimelineCtrl as ctrl',
    };
  });
