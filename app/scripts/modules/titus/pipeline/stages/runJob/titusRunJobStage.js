'use strict';

const angular = require('angular');
import {Subject} from 'rxjs/Subject';

module.exports = angular.module('spinnaker.core.pipeline.stage.titus.runJobStage', [
  require('./runJobExecutionDetails.controller.js')
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'runJob',
      useBaseProvider: true,
      cloudProvider: 'titus',
      providesFor: ['aws', 'titus'],
      templateUrl: require('./runJobStage.html'),
      executionDetailsUrl: require('./runJobExecutionDetails.html'),
      validators: [
        {type: 'requiredField', fieldName: 'cluster.imageId'},
        {type: 'requiredField', fieldName: 'credentials'},
        {type: 'requiredField', fieldName: 'cluster.region'},
        {type: 'requiredField', fieldName: 'cluster.resources.cpu'},
        {type: 'requiredField', fieldName: 'cluster.resources.memory'},
        {type: 'requiredField', fieldName: 'cluster.resources.disk'},
        {type: 'requiredField', fieldName: 'cluster.runtimeLimitSecs'}
      ]
    });
  }).controller('titusRunJobStageCtrl', function ($scope, accountService, $q) {

    let stage = $scope.stage;
    let vm = this;

    if (!stage.cluster) {
      stage.cluster = {};
    }

    $scope.stage.waitForCompletion = ($scope.stage.waitForCompletion === undefined ? true : $scope.stage.waitForCompletion);

    this.loaded = false;
    this.removedGroups = [];
    this.groupsRemovedStream = new Subject();
    this.accountChangedStream = new Subject();
    this.regionChangedStream = new Subject();

    this.accountChanged = () => {
      this.accountChangedStream.next(null);
      setRegistry();
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

    this.onChange = (changes) => {
      stage.registry = changes.registry;
    };

    function setRegistry() {
      if (stage.credentials) {
        stage.registry = $scope.backingData.credentialsKeyedByAccount[stage.credentials].registry;
      }
    }

    function updateImageId() {
      if ($scope.stage.repository && $scope.stage.tag) {
        $scope.stage.cluster.imageId = `${$scope.stage.repository}:${$scope.stage.tag}`;
      }
      else {
        delete $scope.stage.cluster.imageId;
      }
    }

    if ($scope.stage.cluster.imageId) {
      const image = $scope.stage.cluster.imageId;
      $scope.stage.organization = '';
      const parts = image.split('/');
      if (parts.length > 1) {
        $scope.stage.organization = parts.shift();
      }

      const rest = parts.shift().split(':');
      if ($scope.stage.organization) {
        $scope.stage.repository = `${$scope.stage.organization}/${rest.shift()}`;
      } else {
        $scope.stage.repository = rest.shift();
      }
      $scope.stage.tag = rest.shift();
    }

    $scope.$watchGroup(['stage.repository', 'stage.tag'], updateImageId);

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

    stage.cluster.runtimeLimitSecs = stage.cluster.runtimeLimitSecs || 3600;

    stage.deferredInitialization = true;
    $q.all({
      credentialsKeyedByAccount: accountService.getCredentialsKeyedByAccount('titus'),
    }).then((backingData) => {
      backingData.credentials = Object.keys(backingData.credentialsKeyedByAccount);
      $scope.backingData = backingData;

      if (!stage.credentials) {
        stage.credentials = backingData.credentials[0];
      }

      setRegistry();
      return $q.all([]).then(() => {
        vm.updateRegions();
        this.loaded = true;
      });
    });
  });

