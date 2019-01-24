'use strict';

const angular = require('angular');

import { ModalWizard, SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.dcos.serverGroup.configure.clone', [SERVER_GROUP_WRITER, require('../configuration.service').name])
  .controller('dcosCloneServerGroupController', function(
    $scope,
    $uibModalInstance,
    $q,
    $state,
    serverGroupWriter,
    dcosServerGroupConfigurationService,
    serverGroupCommand,
    application,
    title,
    $timeout,
    wizardSubFormValidation,
  ) {
    $scope.pages = {
      templateSelection: require('./templateSelection.html'),
      basicSettings: require('./basicSettings.html'),
      network: require('./network.html'),
      containerSettings: require('./containerSettings.html'),
      environmentVariables: require('./environmentVariables.html'),
      healthChecks: require('./healthChecks.html'),
      volumes: require('./volumes.html'),
      labels: require('./labels.html'),
      optional: require('./optional.html'),
    };

    $scope.title = title;

    $scope.applicationName = application.name;
    $scope.application = application;

    $scope.command = serverGroupCommand;
    $scope.contextImages = serverGroupCommand.viewState.contextImages;

    $scope.state = {
      loaded: false,
      requiresTemplateSelection: !!serverGroupCommand.viewState.requiresTemplateSelection,
    };

    this.templateSelectionText = {
      copied: ['account, region, group, cluster name (stack, details)', 'container configuration'],
      notCopied: [],
    };

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: 'Creating your server group',
      modalInstance: $uibModalInstance,
    });

    function configureCommand() {
      serverGroupCommand.viewState.contextImages = $scope.contextImages;
      $scope.contextImages = null;
      dcosServerGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function() {
        $scope.state.loaded = true; // allows wizard directive to run (after digest).
        $timeout(initializeWizardState); // wait for digest.
        initializeWatches();
      });
    }

    function initializeWatches() {
      $scope.$watch('command.account', $scope.command.accountChanged);
      $scope.$watch('command.group', $scope.command.groupChanged);
      $scope.$watch('command.dcosCluster', $scope.command.dcosClusterChanged);
    }

    function initializeWizardState() {
      wizardSubFormValidation
        .config({ scope: $scope, form: 'form' })
        .register({ page: 'basicSettings', subForm: 'basicSettings' })
        .register({ page: 'network', subForm: 'networkSettings' })
        .register({ page: 'containerSettings', subForm: 'containerSettings' })
        .register({ page: 'environmentVariables', subForm: 'environmentVariables' })
        .register({ page: 'healthChecks', subForm: 'healthChecks' })
        .register({ page: 'volumes', subForm: 'volumes' });
    }

    this.isValid = function() {
      return $scope.command && $scope.command.account !== null && $scope.form.$valid && ModalWizard.isComplete();
    };

    this.showSubmitButton = function() {
      return ModalWizard.allPagesVisited();
    };

    this.clone = function() {
      let command = angular.copy($scope.command);
      if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode === 'createPipeline') {
        return $uibModalInstance.close(command);
      }
      $scope.taskMonitor.submit(function() {
        return serverGroupWriter.cloneServerGroup(command, application);
      });
    };

    this.cancel = function() {
      $uibModalInstance.dismiss();
    };

    if (!$scope.state.requiresTemplateSelection) {
      configureCommand();
    } else {
      $scope.state.loaded = true;
    }

    this.templateSelected = () => {
      $scope.state.requiresTemplateSelection = false;
      configureCommand();
    };
  });
