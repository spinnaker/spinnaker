'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.cloneServerGroup', [
  require('angular-ui-router'),
])
  .controller('cfCloneServerGroupCtrl', function($scope, $modalInstance, _, $q, $state,
                                                  serverGroupWriter, modalWizardService, taskMonitorService,
                                                  cfServerGroupConfigurationService,
                                                  serverGroupCommand, application, title) {
    $scope.pages = {
      templateSelection: require('./templateSelection.html'),
      basicSettings: require('./basicSettings.html'),
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
      forceRefreshMessage: 'Getting your new server group from Cloud Foundry...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

    function configureCommand() {
      cfServerGroupConfigurationService.configureCommand(serverGroupCommand).then(function () {
        $scope.state.loaded = true;
        initializeWizardState();
        initializeSelectOptions();
        initializeWatches();
      });
    }

    function initializeWizardState() {
      var mode = serverGroupCommand.viewState.mode;
      if (mode === 'clone' || mode === 'editPipeline') {
        if ($scope.command.image || $scope.command.viewState.disableImageSelection) {
          modalWizardService.getWizard().markComplete('location');
        }
      }
    }

    function initializeWatches() {
      $scope.$watch('command.credentials', createResultProcessor($scope.command.credentialsChanged));
      $scope.$watch('command.region', createResultProcessor($scope.command.regionChanged));
      $scope.$watch('command.network', createResultProcessor($scope.command.networkChanged));
    }

    function initializeSelectOptions() {
      processCommandUpdateResult($scope.command.credentialsChanged());
      processCommandUpdateResult($scope.command.regionChanged());
      processCommandUpdateResult($scope.command.networkChanged());
    }

    function createResultProcessor(method) {
      return function() {
        processCommandUpdateResult(method());
      };
    }

    function processCommandUpdateResult(result) {
    }

    this.isValid = function () {
      return $scope.command && ($scope.command.viewState.disableImageSelection || $scope.command.image !== null) &&
        ($scope.command.credentials !== null) && ($scope.command.instanceType !== null) &&
        ($scope.command.region !== null) && ($scope.command.zone !== null) &&
        ($scope.command.capacity.desired !== null) &&
        modalWizardService.getWizard().isComplete();
    };

    this.showSubmitButton = function () {
      return modalWizardService.getWizard().allPagesVisited();
    };

    $scope.taskMonitor.onApplicationRefresh = function handleApplicationRefreshComplete() {
      $scope.taskMonitor.task.getCompletedKatoTask().then(function(katoTask) {
        if (katoTask.resultObjects && katoTask.resultObjects.length && katoTask.resultObjects[0].serverGroupNames) {
          var newStateParams = {
            serverGroup: katoTask.resultObjects[0].serverGroupNames[0].split(':')[1],
            accountId: $scope.command.credentials,
            region: $scope.command.region,
            provider: 'cf',
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
      var transformedInstanceMetadata = {};
      // The instanceMetadata is stored using 'key' and 'value' attributes to enable the Add/Remove behavior in the wizard.
      $scope.command.instanceMetadata.forEach(function(metadataPair) {
        transformedInstanceMetadata[metadataPair.key] = metadataPair.value;
      });

      // We use this list of load balancer names when 'Enabling' a server group.
      if ($scope.command.loadBalancers && $scope.command.loadBalancers.length > 0) {
        transformedInstanceMetadata['load-balancer-names'] = $scope.command.loadBalancers.toString();
      }
      $scope.command.instanceMetadata = transformedInstanceMetadata;

      var transformedTags = [];
      // The tags are stored using a 'value' attribute to enable the Add/Remove behavior in the wizard.
      $scope.command.tags.forEach(function(tag) {
        transformedTags.push(tag.value);
      });
      $scope.command.tags = transformedTags;

      $scope.command.targetSize = $scope.command.capacity.desired;
      $scope.command.loadBalancers = $scope.command.loadBalancers;

      // We want min/max set to the same value as desired.
      $scope.command.capacity.min = $scope.command.capacity.desired;
      $scope.command.capacity.max = $scope.command.capacity.desired;

      if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode === 'createPipeline') {
        return $modalInstance.close($scope.command);
      }
      $scope.taskMonitor.submit(
        function() {
          return serverGroupWriter.cloneServerGroup(angular.copy($scope.command), application);
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
  }).name;
