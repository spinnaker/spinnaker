'use strict';

angular.module('deckApp.statusGlyph.directive', [])
  .directive('statusGlyph', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        item: '=',
      },
      templateUrl: 'scripts/modules/tasks/statusGlyph.html',
    };

  });
