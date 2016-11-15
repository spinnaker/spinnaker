'use strict';

import _ from 'lodash';

let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';

module.exports = angular.module('spinnaker.core.pipeline.stage.cf.cloneServerGroupStage', [
  require('core/application/modal/platformHealthOverride.directive.js'),
  ACCOUNT_SERVICE,
  NAMING_SERVICE,
  require('./cloneServerGroupExecutionDetails.controller.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'cloneServerGroup',
      cloudProvider: 'cf',
      templateUrl: require('./cloneServerGroupStage.html'),
      executionDetailsUrl: require('./cloneServerGroupExecutionDetails.html'),
      executionStepLabelUrl: require('./cloneServerGroupStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'region', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'}
      ],
    });
  }).controller('cfCloneServerGroupStageCtrl', function($scope, accountService, namingService, stageConstants) {

    let stage = $scope.stage;

    $scope.viewState = {
      accountsLoaded: false,
    };

    accountService.listAccounts('cf').then((accounts) => {
      $scope.accounts = accounts;
      $scope.viewState.accountsLoaded = true;
    });

    this.cloneTargets = stageConstants.targetList;
    stage.target = stage.target || this.cloneTargets[0].val;
    stage.application = $scope.application.name;
    stage.cloudProvider = 'cf';
    stage.cloudProviderType = 'cf';

    if (stage.isNew && $scope.application.attributes.platformHealthOnlyShowOverride && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['Cloud Foundry'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.cf) {
      stage.credentials = $scope.application.defaultCredentials.cf;
    }

    this.targetClusterUpdated = () => {
      if (stage.targetCluster) {
        let clusterName = namingService.parseServerGroupName(stage.targetCluster);
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

