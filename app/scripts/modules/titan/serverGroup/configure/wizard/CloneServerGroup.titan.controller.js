'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titan.cloneServerGroup', [
  require('angular-ui-router'),
  require('../../../../core/utils/dataConverter.service.js')
])
  .controller('titanCloneServerGroupCtrl', function($scope, $modalInstance, _, $q, $state,
                                                  serverGroupWriter, modalWizardService, taskMonitorService,
                                                  titanServerGroupConfigurationService, dataConverterService,
                                                  serverGroupCommand, application, title) {
    $scope.pages = {
      templateSelection: require('./templateSelection.html'),
      basicSettings: require('./basicSettings.html'),
      resources: require('./resources.html'),
      capacity: require('./capacity.html'),
      parameters: require('./parameters.html'),
    };

    $scope.title = title;
    $scope.applicationName = application.name;
    $scope.application = application;
    $scope.command = serverGroupCommand;
    $scope.state = {
      loaded: false,
      requiresTemplateSelection: !!serverGroupCommand.viewState.requiresTemplateSelection,
    };

    function onApplicationRefresh() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      let [cloneStage] = $scope.taskMonitor.task.execution.stages.filter((stage) => stage.type === 'cloneServerGroup');
      if (cloneStage && cloneStage.context['deploy.server.groups']) {
        let newServerGroupName = cloneStage.context['deploy.server.groups'][$scope.command.region];
        if (newServerGroupName) {
          var newStateParams = {
            serverGroup: newServerGroupName,
            accountId: $scope.command.credentials,
            region: $scope.command.region,
            provider: 'titan',
          };
          var transitionTo = '^.^.^.clusters.serverGroup';
          if ($state.includes('**.clusters.serverGroup')) {  // clone via details, all view
            transitionTo = '^.serverGroup';
          }
          if ($state.includes('**.clusters.cluster.serverGroup')) { // clone or create with details open
            transitionTo = '^.^.serverGroup';
          }
          if ($state.includes('**.clusters')) { // create new, no details open
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

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your server group',
      modalInstance: $modalInstance,
      onTaskComplete: onTaskComplete,
    });

    function configureCommand() {
      titanServerGroupConfigurationService.configureCommand(serverGroupCommand).then(function () {
        $scope.state.loaded = true;
        initializeWizardState();
      });
    }

    function initializeWizardState() {
      var mode = serverGroupCommand.viewState.mode;
      if (mode === 'clone' || mode === 'editPipeline') {
        if ($scope.command.image || $scope.command.viewState.disableImageSelection) {
          modalWizardService.getWizard().markComplete('location');
        }
        modalWizardService.getWizard().markComplete('resources');
        modalWizardService.getWizard().markComplete('capacity');
        modalWizardService.getWizard().markComplete('parameters');
      }
    }

    this.isValid = function () {
      return $scope.command && ($scope.command.viewState.disableImageSelection || $scope.command.image !== null) &&
        ($scope.command.credentials !== null) &&
        ($scope.command.region !== null) &&
        ($scope.command.capacity.desired !== null) &&
        modalWizardService.getWizard().isComplete();
    };

    this.showSubmitButton = function () {
      return modalWizardService.getWizard().allPagesVisited();
    };

    this.clone = function () {
      let command = angular.copy($scope.command);
      command.env = dataConverterService.equalListToKeyValue(command.env);
      if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode === 'createPipeline') {
        return $modalInstance.close(command);
      }
      $scope.taskMonitor.submit(
        function() {
          return serverGroupWriter.cloneServerGroup(command, application);
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };

    if (!$scope.state.requiresTemplateSelection) {
      configureCommand();
    } else {
      $scope.state.loaded = true;
    }

    $scope.$on('template-selected', function() {
      $scope.state.requiresTemplateSelection = false;
      configureCommand();
    });
  });
