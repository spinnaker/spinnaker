'use strict';

angular.module('deckApp')
  .directive('pipelineHeading', function() {
    return {
      restrict: 'E',
      scope: {
        name: '='
      },
      templateUrl: 'views/delivery/pipelineHeading.html'
    };
  });
