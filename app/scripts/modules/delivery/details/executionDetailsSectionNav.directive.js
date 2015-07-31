'use strict';

let angular = require('angular');

require('./executionDetailsSectionNav.html');

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
  }).name;
