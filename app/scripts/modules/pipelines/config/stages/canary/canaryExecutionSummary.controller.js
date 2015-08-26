'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.canary.summary.controller', [
  require('angular-ui-router'),
  require('utils/lodash.js'),
  require('../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
  require('./actions/generateScore.controller.js'),
  require('./actions/endCanary.controller.js'),
])
  .controller('CanaryExecutionSummaryCtrl', function ($scope, $http, settings, $modal) {

    this.generateCanaryScore = function() {
      $modal.open({
        templateUrl: require('./actions/generateScore.modal.html'),
        controller: 'GenerateScoreCtrl as ctrl',
        resolve: {
          canaryId: function() { return $scope.stageSummary.masterStage.context.canary.id; },
        },
      });
    };

    this.endCanary = function() {
      $modal.open({
        templateUrl: require('./actions/endCanary.modal.html'),
        controller: 'EndCanaryCtrl as ctrl',
        resolve: {
          canaryId: function() { return $scope.stageSummary.masterStage.context.canary.id; },
        },
      });
    };

  }).name;
