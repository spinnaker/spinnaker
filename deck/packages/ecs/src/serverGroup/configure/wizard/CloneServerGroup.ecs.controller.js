'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';

import {
  ModalWizard,
  OVERRIDE_REGISTRY,
  SERVER_GROUP_COMMAND_REGISTRY_PROVIDER,
  SERVER_GROUP_WRITER,
  STAGE_ARTIFACT_SELECTOR_COMPONENT_REACT,
  TaskMonitor,
} from '@spinnaker/core';

import { ECS_CLUSTER_READ_SERVICE } from '../../../ecsCluster/ecsCluster.read.service';
import { IAM_ROLE_READ_SERVICE } from '../../../iamRoles/iamRole.read.service';
import { ECS_SECRET_READ_SERVICE } from '../../../secrets/secret.read.service';
import { ECS_SERVER_GROUP_CONFIGURATION_SERVICE } from '../serverGroupConfiguration.service';

export const ECS_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_ECS_CONTROLLER =
  'spinnaker.ecs.cloneServerGroup.controller';
export const name = ECS_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_ECS_CONTROLLER; // for backwards compatibility
module(ECS_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_ECS_CONTROLLER, [
  UIROUTER_ANGULARJS,
  ECS_SERVER_GROUP_CONFIGURATION_SERVICE,
  SERVER_GROUP_WRITER,
  OVERRIDE_REGISTRY,
  SERVER_GROUP_COMMAND_REGISTRY_PROVIDER,
  IAM_ROLE_READ_SERVICE,
  ECS_CLUSTER_READ_SERVICE,
  ECS_SECRET_READ_SERVICE,
  STAGE_ARTIFACT_SELECTOR_COMPONENT_REACT,
]).controller('ecsCloneServerGroupCtrl', [
  '$scope',
  '$uibModalInstance',
  '$q',
  '$state',
  'serverGroupWriter',
  'overrideRegistry',
  'ecsServerGroupConfigurationService',
  'serverGroupCommandRegistry',
  'serverGroupCommand',
  'iamRoleReader',
  'ecsClusterReader',
  'secretReader',
  'application',
  'title',
  function (
    $scope,
    $uibModalInstance,
    $q,
    $state,
    serverGroupWriter,
    overrideRegistry,
    ecsServerGroupConfigurationService,
    serverGroupCommandRegistry,
    serverGroupCommand,
    iamRoleReader,
    ecsClusterReader,
    secretReader,
    application,
    title,
  ) {
    $scope.pages = {
      templateSelection: overrideRegistry.getTemplate(
        'ecs.serverGroup.templateSelection',
        require('./templateSelection/templateSelection.html'),
      ),
      basicSettings: overrideRegistry.getTemplate(
        'ecs.serverGroup.basicSettings',
        require('./location/basicSettings.html'),
      ),
      container: overrideRegistry.getTemplate('ecs.serverGroup.container', require('./container/container.html')),
      horizontalScaling: overrideRegistry.getTemplate(
        'ecs.serverGroup.horizontalScaling',
        require('./horizontalScaling/horizontalScaling.html'),
      ),
      networking: overrideRegistry.getTemplate('ecs.serverGroup.networking', require('./networking/networking.html')),
      logging: overrideRegistry.getTemplate('ecs.serverGroup.logging', require('./logging/logging.html')),
      serviceDiscovery: overrideRegistry.getTemplate(
        'ecs.serverGroup.serviceDiscovery',
        require('./serviceDiscovery/serviceDiscovery.html'),
      ),
      advancedSettings: overrideRegistry.getTemplate(
        'ecs.serverGroup.advancedSettings',
        require('./advancedSettings/advancedSettings.html'),
      ),
      taskDefinition: overrideRegistry.getTemplate(
        'ecs.taskDefinition',
        require('./taskDefinition/taskDefinition.html'),
      ),
    };

    $scope.title = title;

    $scope.applicationName = application.name;
    $scope.application = application;

    $scope.command = serverGroupCommand;

    $scope.state = {
      loaded: false,
      requiresTemplateSelection: !!serverGroupCommand.viewState.requiresTemplateSelection,
    };

    this.templateSelectionText = {
      copied: ['account, region, ecs cluster, stack', 'capacity'],
      notCopied: ['launch type, target group, network mode'],
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
            provider: 'ecs',
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
      ecsServerGroupConfigurationService.configureCommand($scope.command).then(function () {
        $scope.state.loaded = true;
        initializeCommand();
        initializeSelectOptions();
        initializeWatches();
      });
    }

    function initializeWatches() {
      $scope.$watch('command.subnetType', createResultProcessor($scope.command.subnetTypeChanged));
      $scope.$watch('command.credentials', createResultProcessor($scope.command.credentialsChanged));
      $scope.$watch('command.region', createResultProcessor($scope.command.regionChanged));
      $scope.$watch(
        'command.placementStrategyName',
        createResultProcessor($scope.command.placementStrategyNameChanged),
      );
      $scope.$watch('command.stack', () => $scope.command.clusterChanged($scope.command));
      $scope.$watch('command.freeFormDetails', () => $scope.command.clusterChanged($scope.command));
    }

    function initializeSelectOptions() {
      processCommandUpdateResult($scope.command.credentialsChanged(serverGroupCommand));
      processCommandUpdateResult($scope.command.regionChanged(serverGroupCommand));
    }

    function createResultProcessor(method) {
      return function () {
        processCommandUpdateResult(method(serverGroupCommand));
      };
    }

    function processCommandUpdateResult() {
      // TODO(Bruno Carrier) - Implement marking sections either dirty or complete
    }

    function initializeCommand() {}

    this.isValid = function () {
      return true; // TODO(Bruno Carrier) - Implement validation of the form
    };

    this.showSubmitButton = function () {
      return ModalWizard.allPagesVisited();
    };

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

    if (!$scope.state.requiresTemplateSelection) {
      configureCommand();
    } else {
      $scope.state.loaded = true;
    }

    this.templateSelected = () => {
      $scope.state.requiresTemplateSelection = false;
      configureCommand();
    };

    // used by react components to update command fields in parent (angular) scope
    $scope.notifyAngular = function (commandKey, value) {
      if (commandKey == 'pipeline') {
        $scope.command.viewState.pipeline = value;
      } else {
        $scope.command[commandKey] = value;
      }
    };

    // used by react components to configure command for image queries
    $scope.configureCommand = function (query) {
      return ecsServerGroupConfigurationService.configureCommand($scope.command, query);
    };

    // TODO: Migrate horizontalScaling component to react and move this logic there
    $scope.capacityProviderState = {
      useCapacityProviders:
        $scope.command.capacityProviderStrategy && $scope.command.capacityProviderStrategy.length > 0,
      updateComputeOption: function (chosenOption) {
        if (chosenOption == 'launchType') {
          $scope.command.capacityProviderStrategy = [];
        } else if (chosenOption == 'capacityProviders') {
          $scope.command.launchType = '';
          $scope.command.capacityProviderStrategy = $scope.command.capacityProviderStrategy || [];
        }
      },
    };
  },
]);
