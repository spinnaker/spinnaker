'use strict';


angular.module('deckApp')
  .directive('setFocus', function() {
    return {
      restrict: 'A',
      controller: function($scope, $element) {
        $element[0].focus();
      },
    };
  });
