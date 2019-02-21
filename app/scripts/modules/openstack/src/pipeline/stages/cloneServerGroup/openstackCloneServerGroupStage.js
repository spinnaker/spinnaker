'use strict';

const angular = require('angular');
import _ from 'lodash';

import { AccountService, NameUtils, Registry, StageConstants } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.pipeline.stage.cloneServerGroupStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'cloneServerGroup',
      cloudProvider: 'openstack',
      templateUrl: require('./cloneServerGroupStage.html'),
      executionStepLabelUrl: require('./cloneServerGroupStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'region' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('openstackCloneServerGroupStageCtrl', ['$scope', function($scope) {
    let stage = $scope.stage;

    $scope.viewState = {
      accountsLoaded: false,
    };

    AccountService.listAccounts('openstack').then(accounts => {
      $scope.accounts = accounts;
      $scope.viewState.accountsLoaded = true;
    });

    this.cloneTargets = StageConstants.TARGET_LIST;
    stage.target = stage.target || this.cloneTargets[0].val;
    stage.application = $scope.application.name;
    stage.cloudProvider = 'openstack';
    stage.cloudProviderType = 'openstack';

    if (
      stage.isNew &&
      $scope.application.attributes.platformHealthOnlyShowOverride &&
      $scope.application.attributes.platformHealthOnly
    ) {
      stage.interestingHealthProviderNames = ['Openstack'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.openstack) {
      stage.credentials = $scope.application.defaultCredentials.openstack;
    }

    this.targetClusterUpdated = () => {
      if (stage.targetCluster) {
        let clusterName = NameUtils.parseServerGroupName(stage.targetCluster);
        stage.stack = clusterName.stack;
        stage.freeFormDetails = clusterName.freeFormDetails;
      } else {
        stage.stack = '';
        stage.freeFormDetails = '';
      }
    };

    $scope.$watch('stage.targetCluster', this.targetClusterUpdated);

    this.removeCapacity = () => {
      delete stage.capacity;
    };

    if (!_.has(stage, 'useSourceCapacity')) {
      stage.useSourceCapacity = true;
    }

    this.toggleSuspendedProcess = process => {
      stage.suspendedProcesses = stage.suspendedProcesses || [];
      var processIndex = stage.suspendedProcesses.indexOf(process);
      if (processIndex === -1) {
        stage.suspendedProcesses.push(process);
      } else {
        stage.suspendedProcesses.splice(processIndex, 1);
      }
    };

    this.processIsSuspended = process => {
      return stage.suspendedProcesses && stage.suspendedProcesses.includes(process);
    };
  }]);
