'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.titus.runJobStage', [
  require('./runJobExecutionDetails.controller.js')
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'runJob',
      useBaseProvider: true,
      cloudProvider: 'aws',
      templateUrl: require('./runJobStage.html'),
      executionDetailsUrl: require('./runJobExecutionDetails.html'),
      validators: [
        {type: 'requiredField', fieldName: 'cluster.imageId'},
        {type: 'requiredField', fieldName: 'credentials'},
        {type: 'requiredField', fieldName: 'cluster.region'},
        {type: 'requiredField', fieldName: 'cluster.resources.cpu'},
        {type: 'requiredField', fieldName: 'cluster.resources.memory'},
        {type: 'requiredField', fieldName: 'cluster.resources.disk'}
      ]
    });
  }).controller('titusRunJobStageCtrl', function ($scope, accountService, $q) {

    let stage = $scope.stage;
    let vm = this;

    if (!stage.cluster) {
      stage.cluster = {};
    }

    vm.updateRegions = function () {
      if (stage.credentials) {
        $scope.regions = $scope.backingData.credentialsKeyedByAccount[stage.credentials].regions;
        if (!_.includes(_.map($scope.regions, 'name'), stage.cluster.region)) {
          delete stage.cluster.region;
        }
      } else {
        $scope.regions = null;
      }
    };

    if (!stage.credentials && $scope.application.defaultCredentials.titus) {
      stage.credentials = $scope.application.defaultCredentials.titus;
    }

    if (!stage.cluster.env) {
      stage.cluster.env = {};
    }

    if (!stage.cluster.application) {
      stage.cluster.application = $scope.application.name;
    }

    if (!stage.cloudProvider) {
      stage.cloudProvider = 'titus';
    }

    if (!stage.cluster.capacity) {
      stage.cluster.capacity = {
        min: 1,
        max: 1,
        desired: 1
      };
    }

    $q.all({
      credentialsKeyedByAccount: accountService.getCredentialsKeyedByAccount('titus'),
    }).then(function (backingData) {
      backingData.credentials = _.keys(backingData.credentialsKeyedByAccount);
      $scope.backingData = backingData;
      return $q.all([]).then(function () {
        if (stage.credentials) {
          vm.updateRegions();
        }
        $scope.$watch('stage.credentials', vm.updateRegions);
      });
    });

  });

