'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary.summary.controller', [
  require('angular-ui-router'),
  require('../../../../core/utils/lodash.js'),
  require('../../../../core/delivery/details/executionDetailsSection.service.js'),
  require('../../../../core/delivery/details/executionDetailsSectionNav.directive.js'),
  require('./actions/generateScore.controller.js'),
  require('./actions/endCanary.controller.js'),
])
  .controller('CanaryExecutionSummaryCtrl', function ($scope, $http, settings, $uibModal) {

    this.generateCanaryScore = function() {
      $uibModal.open({
        templateUrl: require('./actions/generateScore.modal.html'),
        controller: 'GenerateScoreCtrl as ctrl',
        resolve: {
          canaryId: function() { return $scope.stageSummary.masterStage.context.canary.id; },
        },
      });
    };

    this.endCanary = function() {
      $uibModal.open({
        templateUrl: require('./actions/endCanary.modal.html'),
        controller: 'EndCanaryCtrl as ctrl',
        resolve: {
          canaryId: function() { return $scope.stageSummary.masterStage.context.canary.id; },
        },
      });
    };

  });
