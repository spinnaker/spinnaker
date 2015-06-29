'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.executionDetails.section.nav.directive', [
])
  .directive('executionDetailsSectionNav', function() {
    return {
      restrict: 'E',
      templateUrl: require('./executionDetailsSectionNav.html'),
      scope: {
        sections: '=',
      }
    };
  });
