'use strict';

angular.module('spinnaker')
  .directive('autoFocus', function($timeout) {
    return {
      restrict: 'A',
      link: function(scope, elem) {
        $timeout(function() { elem.focus(); });
      }
    };
  });
