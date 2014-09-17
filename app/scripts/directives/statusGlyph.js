'use strict';

angular.module('deckApp')
  .directive('statusGlyph', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        item: '=',
      },
      templateUrl: 'views/statusglyph.html',
    };

  });
