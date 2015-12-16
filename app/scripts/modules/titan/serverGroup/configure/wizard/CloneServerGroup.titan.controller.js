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
    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your server group',
      forceRefreshMessage: 'Getting your new server group from Titan...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
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

    $scope.taskMonitor.onApplicationRefresh = function handleApplicationRefreshComplete() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $scope.taskMonitor.task.getCompletedKatoTask().then(function(katoTask) {
        if (katoTask.resultObjects && katoTask.resultObjects.length && katoTask.resultObjects[0].serverGroupNames) {
          var newStateParams = {
            serverGroup: katoTask.resultObjects[0].serverGroupNames[0].split(':')[1],
            accountId: $scope.command.credentials,
            region: $scope.command.region,
            provider: 'titan',
          };
          if (!$state.includes('**.clusters.**')) {
            $state.go('^.^.^.clusters.serverGroup', newStateParams);
          } else {
            if ($state.includes('**.serverGroup')) {
              $state.go('^.serverGroup', newStateParams);
            } else {
              if ($state.includes('**.clusters.*')) {
                $state.go('^.serverGroup', newStateParams);
              } else {
                $state.go('.serverGroup', newStateParams);
              }
            }
          }
        }
      });
    };

    this.clone = function () {
      $scope.command.capacity.min = $scope.command.capacity.desired;  // We want min/max set to the same value as desired
      $scope.command.capacity.max = $scope.command.capacity.desired;
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
