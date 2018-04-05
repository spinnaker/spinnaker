'use strict';

const angular = require('angular');
import _ from 'lodash';

import { ACCOUNT_SERVICE, NAMING_SERVICE, ARTIFACT_REFERENCE_SERVICE_PROVIDER, StageConstants } from '@spinnaker/core';

module.exports = angular.module('spinnaker.gce.pipeline.stage..cloneServerGroupStage', [
  ACCOUNT_SERVICE,
  NAMING_SERVICE,
  ARTIFACT_REFERENCE_SERVICE_PROVIDER,
])
  .config(function(pipelineConfigProvider, artifactReferenceServiceProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'cloneServerGroup',
      cloudProvider: 'gce',
      templateUrl: require('./cloneServerGroupStage.html'),
      executionStepLabelUrl: require('./cloneServerGroupStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'region', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'}
      ],
    });

    artifactReferenceServiceProvider.registerReference('stage', obj => {
      const paths = [];
      if (obj.type === 'deploy' && Array.isArray(obj.clusters)) {
        obj.clusters.forEach((cluster, i) => {
          if (cluster.cloudProvider === 'gce') {
            paths.push(['clusters', i, 'imageArtifactId']);
          }
        });
      }
      return paths;
    });

  }).controller('gceCloneServerGroupStageCtrl', function($scope, accountService, namingService) {
    let stage = $scope.stage;

    $scope.viewState = {
      accountsLoaded: false,
    };

    accountService.listAccounts('gce').then((accounts) => {
      $scope.accounts = accounts;
      $scope.viewState.accountsLoaded = true;
    });

    this.cloneTargets = StageConstants.TARGET_LIST;
    stage.target = stage.target || this.cloneTargets[0].val;
    stage.application = $scope.application.name;
    stage.cloudProvider = 'gce';
    stage.cloudProviderType = 'gce';

    if (stage.isNew && $scope.application.attributes.platformHealthOnlyShowOverride && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['Google'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.gce) {
      stage.credentials = $scope.application.defaultCredentials.gce;
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

