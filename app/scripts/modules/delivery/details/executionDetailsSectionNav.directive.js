'use strict';

angular.module('spinnaker.executionDetails.section.nav.directive', [])
  .directive('executionDetailsSectionNav', function() {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/delivery/details/executionDetailsSectionNav.html',
      scope: {
        sections: '=',
      }
    };
  });
