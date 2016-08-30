'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.kubernetes.runJobStage', [
  require('../../../../core/utils/lodash.js'),
  require('../../../../docker/image/dockerImageAndTagSelector.component.js'),
  require('../../../container/commands.component.js'),
  require('../../../container/arguments.component.js'),
  require('../../../container/environmentVariables.component.js'),
  require('../../../container/volumes.component.js'),
  require('./runJobExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'runJob',
      cloudProvider: 'kubernetes',
      templateUrl: require('./runJobStage.html'),
      executionDetailsUrl: require('./runJobExecutionDetails.html'),
    });
  }).controller('kubernetesRunJobStageCtrl', function($scope, accountService, _) {
    this.stage = $scope.stage;
    if (!_.has(this.stage, 'container.name')) {
      _.set(this.stage, 'container.name', Date.now().toString());
    }

    accountService.getUniqueAttributeForAllAccounts('namespaces')('kubernetes')
      .then((namespaces) => {
        this.namespaces = namespaces;
      });

    accountService.listAccounts('kubernetes')
      .then((accounts) => {
        this.accounts = accounts;
      });

    this.stage.cloudProvider = 'kubernetes';
    this.stage.application = $scope.application.name;

    if (!this.stage.credentials && $scope.application.defaultCredentials.kubernetes) {
      this.stage.credentials = $scope.application.defaultCredentials.kubernetes;
    }
  });
