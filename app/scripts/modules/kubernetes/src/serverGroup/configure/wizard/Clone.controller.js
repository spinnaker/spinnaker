'use strict';

const angular = require('angular');

import { FirewallLabels, SERVER_GROUP_WRITER, TaskMonitor, ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.serverGroup.configure.kubernetes.clone', [
    require('@uirouter/angularjs').default,
    SERVER_GROUP_WRITER,
    require('../configuration.service').name,
  ])
  .controller('kubernetesCloneServerGroupController', ['$scope', '$uibModalInstance', '$q', '$state', 'serverGroupWriter', 'kubernetesServerGroupConfigurationService', 'serverGroupCommand', 'application', 'title', '$timeout', 'wizardSubFormValidation', function(
    $scope,
    $uibModalInstance,
    $q,
    $state,
    serverGroupWriter,
    kubernetesServerGroupConfigurationService,
    serverGroupCommand,
    application,
    title,
    $timeout,
    wizardSubFormValidation,
  ) {
    $scope.pages = {
      templateSelection: require('./templateSelection.html'),
      basicSettings: require('./basicSettings.html'),
      deployment: require('./deployment.html'),
      loadBalancers: require('./loadBalancers.html'),
      replicas: require('./replicas.html'),
      volumes: require('./volumes.html'),
      advancedSettings: require('./advancedSettings.html'),
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
      copied: [
        'account, namespace, cluster name (stack, details)',
        'load balancers',
        FirewallLabels.get('firewalls'),
        'container configuration',
      ],
      notCopied: [],
    };

    if (!$scope.command.viewState.disableStrategySelection) {
      this.templateSelectionText.notCopied.push(
        'the deployment strategy (if any) used to deploy the most recent server group',
      );
    }

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: 'Creating your server group',
      modalInstance: $uibModalInstance,
    });

    function configureCommand() {
      serverGroupCommand.viewState.contextImages = $scope.contextImages;
      $scope.contextImages = null;
      kubernetesServerGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function() {
        $scope.state.loaded = true; // allows wizard directive to run (after digest).
        $timeout(initializeWizardState); // wait for digest.
        initializeWatches();
      });
    }

    function initializeWatches() {
      $scope.$watch('command.account', $scope.command.accountChanged);
      $scope.$watch('command.namespace', $scope.command.namespaceChanged);
    }

    function initializeWizardState() {
      var mode = serverGroupCommand.viewState.mode;
      if (mode === 'clone' || mode === 'editPipeline') {
        ModalWizard.markComplete('location');
        ModalWizard.markComplete('deployment');
        ModalWizard.markComplete('load-balancers');
        ModalWizard.markComplete('replicas');
        ModalWizard.markComplete('volumes');
      }

      wizardSubFormValidation
        .config({ scope: $scope, form: 'form' })
        .register({ page: 'location', subForm: 'basicSettings' })
        .register({ page: 'advanced-settings', subForm: 'advancedSettings' });
    }

    this.isValid = function() {
      return (
        $scope.command &&
        $scope.command.containers.length > 0 &&
        $scope.command.account !== null &&
        ModalWizard.isComplete() &&
        wizardSubFormValidation.subFormsAreValid()
      );
    };

    this.showSubmitButton = function() {
      return ModalWizard.allPagesVisited();
    };

    this.clone = function() {
      if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode === 'createPipeline') {
        return $uibModalInstance.close($scope.command);
      }
      $scope.taskMonitor.submit(function() {
        return serverGroupWriter.cloneServerGroup(angular.copy($scope.command), application);
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
  }]);
