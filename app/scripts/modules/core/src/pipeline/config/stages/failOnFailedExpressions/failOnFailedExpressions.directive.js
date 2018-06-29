'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.failOnFailedExpressions.directive', [])
  .component('failOnFailedExpressions', {
    bindings: {
      stage: '<',
    },
    templateUrl: require('./failOnFailedExpressions.directive.html'),
  });
