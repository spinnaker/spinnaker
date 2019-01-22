'use strict';

const angular = require('angular');

import { SERVER_GROUP_WRITER, TaskMonitor, ModalWizard, FirewallLabels } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.cloneServerGroup.controller', [
    require('@uirouter/angularjs').default,
    require('../serverGroupConfiguration.service.js').name,
    require('../../serverGroup.transformer.js').name,
    SERVER_GROUP_WRITER,
  ])
  .controller('azureCloneServerGroupCtrl', function(
    $scope,
    $uibModalInstance,
    $q,
    $state,
    serverGroupWriter,
    azureServerGroupConfigurationService,
    serverGroupCommand,
    application,
    title,
  ) {
    $scope.pages = {
      templateSelection: require('./templateSelection.html'),
      basicSettings: require('./basicSettings/basicSettings.html'),
      loadBalancers: require('./loadBalancers/loadBalancers.html'),
      networkSettings: require('./networkSettings/networkSettings.html'),
      securityGroups: require('./securityGroup/securityGroups.html'),
      advancedSettings: require('./advancedSettings/advancedSettings.html'),
    };

    $scope.firewallsLabel = FirewallLabels.get('Firewalls');

    $scope.title = title;

    $scope.applicationName = application.name;
    $scope.application = application;
    $scope.command = serverGroupCommand;

    // Give regions an init value to prevent it being undefined. If so, the React component RegionSelectField would get "undefined" for property regions
    // and then be unmounted so that the region selector would be hidden.
    $scope.command.backingData = $scope.command.backingData || {};
    $scope.command.backingData.filtered = $scope.command.backingData.filtered || {};
    $scope.command.backingData.filtered.regions = $scope.command.backingData.filtered.regions || [];

    $scope.state = {
      loaded: false,
      requiresTemplateSelection: !!serverGroupCommand.viewState.requiresTemplateSelection,
    };

    this.templateSelectionText = {
      copied: [
        'account, region, subnet, cluster name (stack, details)',
        'load balancers',
        FirewallLabels.get('firewalls'),
        'instance type',
        'all fields on the Advanced Settings page',
      ],
      notCopied: [],
      additionalCopyText:
        'If a server group exists in this cluster at the time of deployment, its scaling policies will be copied over to the new server group.',
    };

    if (!$scope.command.viewState.disableStrategySelection) {
      this.templateSelectionText.notCopied.push(
        'the deployment strategy (if any) used to deploy the most recent server group',
      );
    }

    function onApplicationRefresh() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      let cloneStage = $scope.taskMonitor.task.execution.stages.find(stage => stage.type === 'cloneServerGroup');
      if (cloneStage && cloneStage.context['deploy.server.groups']) {
        let newServerGroupName = cloneStage.context['deploy.server.groups'][$scope.command.region];
        if (newServerGroupName) {
          var newStateParams = {
            serverGroup: newServerGroupName,
            accountId: $scope.command.credentials,
            region: $scope.command.region,
            provider: 'azure',
          };
          var transitionTo = '^.^.^.clusters.serverGroup';
          if ($state.includes('**.clusters.serverGroup')) {
            // clone via details, all view
            transitionTo = '^.serverGroup';
          }
          if ($state.includes('**.clusters.cluster.serverGroup')) {
            // clone or create with details open
            transitionTo = '^.^.serverGroup';
          }
          if ($state.includes('**.clusters')) {
            // create new, no details open
            transitionTo = '.serverGroup';
          }
          $state.go(transitionTo, newStateParams);
        }
      }
    }

    function onTaskComplete() {
      application.serverGroups.refresh();
      application.serverGroups.onNextRefresh($scope, onApplicationRefresh);
    }

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: 'Creating your server group',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    function configureCommand() {
      azureServerGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function() {
        var mode = serverGroupCommand.viewState.mode;
        if (mode === 'clone' || mode === 'create') {
          serverGroupCommand.viewState.useAllImageSelection = true;
        }
        $scope.state.loaded = true;
        initializeWizardState();
        initializeSelectOptions();
        initializeWatches();
      });
    }

    function initializeWizardState() {
      var mode = serverGroupCommand.viewState.mode;
      if (mode === 'clone' || mode === 'editPipeline') {
        ModalWizard.markComplete('basic-settings');
        ModalWizard.markComplete('load-balancers');
        ModalWizard.markComplete('network-settings');
        ModalWizard.markComplete('security-groups');
      }
    }

    function initializeWatches() {
      $scope.$watch('command.credentials', createResultProcessor($scope.command.credentialsChanged));
      $scope.$watch('command.region', createResultProcessor($scope.command.regionChanged));
    }

    function initializeSelectOptions() {
      processCommandUpdateResult($scope.command.credentialsChanged($scope.command));
      processCommandUpdateResult($scope.command.regionChanged($scope.command));
    }

    function createResultProcessor(method) {
      return function() {
        processCommandUpdateResult(method($scope.command));
      };
    }

    function processCommandUpdateResult(result) {
      if (result.dirty.loadBalancers) {
        ModalWizard.markDirty('load-balancers');
        ModalWizard.markDirty('network-settings');
      }
      if (result.dirty.securityGroups) {
        ModalWizard.markDirty('security-groups');
      }
    }

    this.submit = function() {
      if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode === 'createPipeline') {
        return $uibModalInstance.close($scope.command);
      }
      $scope.taskMonitor.submit(function() {
        return serverGroupWriter.cloneServerGroup($scope.command, application);
      });
    };

    this.cancel = function() {
      $uibModalInstance.dismiss();
    };

    this.toggleSuspendedProcess = function(process) {
      $scope.command.suspendedProcesses = $scope.command.suspendedProcesses || [];
      var processIndex = $scope.command.suspendedProcesses.indexOf(process);
      if (processIndex === -1) {
        $scope.command.suspendedProcesses.push(process);
      } else {
        $scope.command.suspendedProcesses.splice(processIndex, 1);
      }
    };

    this.processIsSuspended = function(process) {
      return $scope.command.suspendedProcesses.includes(process);
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
