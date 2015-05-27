'use strict';

angular.module('spinnaker.statusGlyph.directive', [])
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
