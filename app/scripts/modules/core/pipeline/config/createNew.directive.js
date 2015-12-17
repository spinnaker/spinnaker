'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.pipeline.createNew.directive', [
  ])
  .directive('createNew', function() {
    return {
      restrict: 'E',
      scope: {
        application: '='
      },
      templateUrl: require('./createNew.html'),
    };
  });
