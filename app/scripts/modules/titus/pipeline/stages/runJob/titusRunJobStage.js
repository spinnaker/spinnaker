'use strict';

let angular = require('angular');
import {Subject} from 'rxjs/Subject';

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

    this.loaded = false;
    this.removedGroups = [];
    this.groupsRemovedStream = new Subject();
    this.accountChangedStream = new Subject();
    this.regionChangedStream = new Subject();

    this.accountChanged = () => {
      this.accountChangedStream.next(null);
      this.updateRegions();
    };

    this.regionChanged = () => {
      this.regionChangedStream.next(null);
    };

    this.updateRegions = () => {
      if (stage.credentials) {
        $scope.regions = $scope.backingData.credentialsKeyedByAccount[stage.credentials].regions;
        if ($scope.regions.map(r => r.name).every(r => r !== stage.cluster.region)) {
          this.regionChanged();
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

    if (!stage.cluster.securityGroups) {
      stage.cluster.securityGroups = [];
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
    }).then((backingData) => {
      backingData.credentials = Object.keys(backingData.credentialsKeyedByAccount);
      $scope.backingData = backingData;
      return $q.all([]).then(() => {
        if (stage.credentials) {
          vm.updateRegions();
        }
        this.loaded = true;
      });
    });

  });

