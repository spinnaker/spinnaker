'use strict';

const angular = require('angular');

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.pipeline.stage.destroyAsgStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'destroyServerGroup',
      cloudProvider: 'openstack',
      templateUrl: require('./destroyAsgStage.html'),
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
      accountExtractor: stage => [stage.context.credentials],
      configAccountExtractor: stage => [stage.credentials],
      validators: [
        {
          type: 'targetImpedance',
          message:
            'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('openstackDestroyAsgStageCtrl', [
    '$scope',
    function($scope) {
      let stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('openstack').then(function(accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      $scope.targets = StageConstants.TARGET_LIST;

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'openstack';

      if (!stage.credentials && $scope.application.defaultCredentials.openstack) {
        stage.credentials = $scope.application.defaultCredentials.openstack;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.openstack) {
        stage.regions.push($scope.application.defaultRegions.openstack);
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }
    },
  ]);
