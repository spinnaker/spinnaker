'use strict';

angular
  .module('deckApp')
  .directive('modalClose', function () {
    return {
      scope:true,
      restrict: 'E',
      templateUrl: 'views/directives/modalClose.html'
    }
  });