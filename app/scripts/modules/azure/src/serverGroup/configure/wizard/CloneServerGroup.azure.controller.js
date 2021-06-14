'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';

import { FirewallLabels, ModalWizard, SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

import { AZURE_SERVERGROUP_SERVERGROUP_TRANSFORMER } from '../../serverGroup.transformer';
import { AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE } from '../serverGroupConfiguration.service';
import Utility from '../../../utility';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_AZURE_CONTROLLER =
  'spinnaker.azure.cloneServerGroup.controller';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_AZURE_CONTROLLER; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_AZURE_CONTROLLER, [
  UIROUTER_ANGULARJS,
  AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE,
  AZURE_SERVERGROUP_SERVERGROUP_TRANSFORMER,
  SERVER_GROUP_WRITER,
]).controller('azureCloneServerGroupCtrl', [
  '$scope',
  '$uibModalInstance',
  '$q',
  '$state',
  'serverGroupWriter',
  'azureServerGroupConfigurationService',
  'serverGroupCommand',
  'application',
  'title',
  function (
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
      instanceType: require('./instanceType/instanceType.html'),
      zones: require('./capacity/zones.html'),
      tags: require('./tags/tags.html'),
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
      const cloneStage = $scope.taskMonitor.task.execution.stages.find((stage) => stage.type === 'cloneServerGroup');
      if (cloneStage && cloneStage.context['deploy.server.groups']) {
        const newServerGroupName = cloneStage.context['deploy.server.groups'][$scope.command.region];
        if (newServerGroupName) {
          const newStateParams = {
            serverGroup: newServerGroupName,
            accountId: $scope.command.credentials,
            region: $scope.command.region,
            provider: 'azure',
          };
          let transitionTo = '^.^.^.clusters.serverGroup';
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
      azureServerGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function () {
        const mode = serverGroupCommand.viewState.mode;
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
      const mode = serverGroupCommand.viewState.mode;
      if (mode === 'clone' || mode === 'editPipeline') {
        ModalWizard.markComplete('basic-settings');
        ModalWizard.markComplete('load-balancers');
        ModalWizard.markComplete('network-settings');
        ModalWizard.markComplete('security-groups');
        ModalWizard.markComplete('instance-type');
        ModalWizard.markComplete('zones');
      }
    }

    function initializeWatches() {
      $scope.$watch('command.credentials', createResultProcessor($scope.command.credentialsChanged));
      $scope.$watch('command.region', createResultProcessor($scope.command.regionChanged));
    }

    function initializeSelectOptions() {
      processCommandUpdateResult($scope.command.credentialsChanged($scope.command, true));
      processCommandUpdateResult($scope.command.regionChanged($scope.command, true));
    }

    function createResultProcessor(method) {
      return function (newValue, oldValue) {
        if (newValue !== oldValue) {
          processCommandUpdateResult(method($scope.command));
        }
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
      if (result.dirty.instanceType) {
        ModalWizard.markDirty('instance-type');
      }
      if (result.dirty.zoneEnabled || result.dirty.zones) {
        ModalWizard.markDirty('zones');
      }
    }

    this.submit = function () {
      if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode === 'createPipeline') {
        return $uibModalInstance.close($scope.command);
      }
      $scope.taskMonitor.submit(function () {
        return serverGroupWriter.cloneServerGroup($scope.command, application);
      });
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };

    this.toggleSuspendedProcess = function (process) {
      $scope.command.suspendedProcesses = $scope.command.suspendedProcesses || [];
      const processIndex = $scope.command.suspendedProcesses.indexOf(process);
      if (processIndex === -1) {
        $scope.command.suspendedProcesses.push(process);
      } else {
        $scope.command.suspendedProcesses.splice(processIndex, 1);
      }
    };

    this.processIsSuspended = function (process) {
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

    this.isValid = function () {
      return (
        $scope.command &&
        $scope.command.application &&
        $scope.command.credentials &&
        $scope.command.instanceType &&
        $scope.command.region &&
        (!$scope.command.zonesEnabled || $scope.command.zones.length !== 0) &&
        Utility.checkTags($scope.command.instanceTags).isValid
      );
    };
  },
]);
