'use strict';

const angular = require('angular');
import _ from 'lodash';

import { AccountService, NameUtils, StageConstants } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.cf.pipeline.stage.cloneServerGroupStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'cloneServerGroup',
      cloudProvider: 'cf',
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
  .controller('cfCloneServerGroupStageCtrl', function($scope) {
    let stage = $scope.stage;

    $scope.viewState = {
      accountsLoaded: false,
    };

    AccountService.listAccounts('cf').then(accounts => {
      $scope.accounts = accounts;
      $scope.viewState.accountsLoaded = true;
    });

    this.cloneTargets = StageConstants.TARGET_LIST;
    stage.target = stage.target || this.cloneTargets[0].val;
    stage.application = $scope.application.name;
    stage.cloudProvider = 'cf';
    stage.cloudProviderType = 'cf';

    if (
      stage.isNew &&
      $scope.application.attributes.platformHealthOnlyShowOverride &&
      $scope.application.attributes.platformHealthOnly
    ) {
      stage.interestingHealthProviderNames = ['Cloud Foundry'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.cf) {
      stage.credentials = $scope.application.defaultCredentials.cf;
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

    this.toggleDisableTraffic = () => {
      stage.disableTraffic = !stage.disableTraffic;
    };
  });
