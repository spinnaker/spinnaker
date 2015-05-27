'use strict';

angular
  .module('spinnaker')
  .directive('modalClose', function () {
    return {
      scope:true,
      restrict: 'E',
      templateUrl: 'views/directives/modalClose.html'
    };
  });
