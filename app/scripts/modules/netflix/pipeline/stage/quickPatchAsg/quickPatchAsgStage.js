'use strict';

let angular = require('angular');

import {LIST_EXTRACTOR_SERVICE} from 'core/application/listExtractor/listExtractor.service';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.quickPatchAsgStage', [
  require('core/pipeline/config/pipelineConfigProvider.js'),
  LIST_EXTRACTOR_SERVICE,
  require('core/config/settings.js'),
  require('core/widgets')
])
  .config(function(pipelineConfigProvider, settings) {
    if (settings.feature && settings.feature.netflixMode) {
      pipelineConfigProvider.registerStage({
        label: 'Quick Patch Server Group',
        description: 'Quick Patches a server group',
        extendedDescription: `<a target="_blank" href="https://confluence.netflix.com/display/ENGTOOLS/Quick+Patch+In+Spinnaker">
          <span class="small glyphicon glyphicon-file"></span> Documentation</a>`,
        key: 'quickPatch',
        controller: 'QuickPatchAsgStageCtrl',
        controllerAs: 'QuickPatchAsgStageCtrl',
        templateUrl: require('./quickPatchAsgStage.html'),
        executionDetailsUrl: require('./quickPatchAsgExecutionDetails.html'),
        validators: [
          {
            type: 'stageOrTriggerBeforeType',
            stageType: 'jenkins',
            checkParentTriggers: true,
            message: 'You must have a Jenkins stage or trigger before a Quick Patch stage.'
          },
          {type: 'requiredField', fieldName: 'clusterName', fieldLabel: 'cluster'},
          {type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
          {type: 'requiredField', fieldName: 'region'},
          {type: 'requiredField', fieldName: 'package'},
          {type: 'requiredField', fieldName: 'baseOs'},
        ],
      });
    }
  }).controller('QuickPatchAsgStageCtrl', function($scope, stage, bakeryService, accountService, appListExtractorService) {
    $scope.stage = stage;
    $scope.baseOsOptions = ['ubuntu', 'centos'];
    $scope.stage.application = $scope.application.name;
    $scope.stage.healthProviders = ['Discovery'];

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts().then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.accountUpdated = function() {
      let accountFilter = (cluster) => cluster ? cluster.account === $scope.stage.credentials : true;

      $scope.regions = appListExtractorService.getRegions([$scope.application], accountFilter)
        .map((region) => ({name: region}));

      $scope.regionsLoaded = true;
      $scope.stage.account = $scope.stage.credentials;
    };

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

