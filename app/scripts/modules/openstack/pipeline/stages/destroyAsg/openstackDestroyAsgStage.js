'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.openstack.destroyAsgStage', [
  require('../../../../core/pipeline/config/stages/stageConstants.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'destroyServerGroup',
      cloudProvider: 'openstack',
      templateUrl: require('./destroyAsgStage.html'),
      executionDetailsUrl: require('../../../../core/pipeline/config/stages/destroyAsg/templates/destroyAsgExecutionDetails.template.html'),
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.'
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('openstackDestroyAsgStageCtrl', function($scope, accountService, stageConstants) {

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts('openstack').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.targets = stageConstants.targetList;

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

  });
