'use strict';

const angular = require('angular');
import { Subject } from 'rxjs';

import { AccountService, ExecutionDetailsTasks, FirewallLabels, Registry } from '@spinnaker/core';

import { RunJobExecutionDetails } from './RunJobExecutionDetails';
import { SECURITY_GROUPS_REMOVED } from './securityGroupsRemoved.component';

module.exports = angular
  .module('spinnaker.titus.pipeline.stage.runJobStage', [
    SECURITY_GROUPS_REMOVED,
    require('./securityGroupSelector.directive').name,
  ])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'runJob',
      useBaseProvider: true,
      restartable: true,
      cloudProvider: 'titus',
      providesFor: ['aws', 'titus'],
      templateUrl: require('./runJobStage.html'),
      executionDetailsSections: [RunJobExecutionDetails, ExecutionDetailsTasks],
      defaultTimeoutMs: 2 * 60 * 60 * 1000, // 2 hours
      validators: [
        { type: 'requiredField', fieldName: 'cluster.imageId' },
        { type: 'requiredField', fieldName: 'credentials' },
        { type: 'requiredField', fieldName: 'cluster.region' },
        { type: 'requiredField', fieldName: 'cluster.resources.cpu' },
        { type: 'requiredField', fieldName: 'cluster.resources.gpu' },
        { type: 'requiredField', fieldName: 'cluster.resources.memory' },
        { type: 'requiredField', fieldName: 'cluster.resources.disk' },
        { type: 'requiredField', fieldName: 'cluster.runtimeLimitSecs' },
      ],
    });
  })
  .controller('titusRunJobStageCtrl', function($scope, $q) {
    let stage = $scope.stage;
    let vm = this;
    $scope.firewallsLabel = FirewallLabels.get('Firewalls');

    if (!stage.cluster) {
      stage.cluster = {};
    }

    $scope.stage.waitForCompletion =
      $scope.stage.waitForCompletion === undefined ? true : $scope.stage.waitForCompletion;

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

    this.onChange = changes => {
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
      } else {
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

    if (!stage.cluster.resources) {
      stage.cluster.resources = {};
    }

    if (!stage.cluster.capacity) {
      stage.cluster.capacity = {
        min: 1,
        max: 1,
        desired: 1,
      };
    }

    stage.cluster.runtimeLimitSecs = stage.cluster.runtimeLimitSecs || 3600;
    stage.cluster.resources.gpu = stage.cluster.resources.gpu || 0;
    stage.cluster.resources.cpu = stage.cluster.resources.cpu || 1;
    stage.cluster.resources.disk = stage.cluster.resources.disk || 10000;
    stage.cluster.retries = stage.cluster.retries || 0;
    stage.cluster.resources.memory = stage.cluster.resources.memory || 512;
    stage.cluster.resources.networkMbps = stage.cluster.resources.networkMbps || 128;

    stage.deferredInitialization = true;
    $q.all({
      credentialsKeyedByAccount: AccountService.getCredentialsKeyedByAccount('titus'),
    }).then(backingData => {
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
