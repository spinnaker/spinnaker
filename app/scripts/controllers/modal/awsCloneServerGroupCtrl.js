'use strict';


angular.module('deckApp.aws')
  .controller('awsCloneServerGroupCtrl', function($scope, $modalInstance, _, $q, $exceptionHandler, $state, settings,
                                               orcaService, modalWizardService, taskMonitorService,
                                               serverGroupConfigurationService, serverGroupCommand, application, title) {
    $scope.title = title;

    $scope.applicationName = application.name;

    $scope.command = serverGroupCommand;

    $scope.state = {
      loaded: false,
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your server group',
      forceRefreshMessage: 'Getting your new server group from Amazon...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

    serverGroupConfigurationService.configureCommand(application, serverGroupCommand).then(function() {
      if (!serverGroupCommand.backingData.packageImages.length) {
        serverGroupCommand.viewState.useAllImageSelection = true;
      }
      $scope.state.loaded = true;
      initializeCommand();
      initializeWizardState();
      initializeSelectOptions();
      initializeWatches();
    });

    function initializeWizardState() {
      if (serverGroupCommand.viewState.mode === 'clone') {
        modalWizardService.getWizard().markComplete('location');
        modalWizardService.getWizard().markComplete('load-balancers');
        modalWizardService.getWizard().markComplete('security-groups');
        modalWizardService.getWizard().markComplete('instance-profile');
        modalWizardService.getWizard().markComplete('capacity');
        modalWizardService.getWizard().markComplete('advanced');
      }
    }

    function initializeWatches() {
      $scope.$watch('command.credentials', createResultProcessor($scope.command.credentialsChanged));
      $scope.$watch('command.region', createResultProcessor($scope.command.regionChanged));
      $scope.$watch('command.subnetType', createResultProcessor($scope.command.subnetChanged));
      $scope.$watch('command.viewState.usePreferredZones', createResultProcessor($scope.command.usePreferredZonesChanged));
    }

    // TODO: Move to service
    function initializeSelectOptions() {
      processCommandUpdateResult($scope.command.credentialsChanged());
      processCommandUpdateResult($scope.command.regionChanged());
      serverGroupConfigurationService.configureSubnetPurposes($scope.command);
    }

    function createResultProcessor(method) {
      return function() {
        processCommandUpdateResult(method());
      }
    }

    function processCommandUpdateResult(result) {
      if (result.dirty.loadBalancers) {
        modalWizardService.getWizard().markDirty('load-balancers');
      }
      if (result.dirty.securityGroups) {
        modalWizardService.getWizard().markDirty('security-groups');
      }
      if (result.dirty.availabilityZones) {
        modalWizardService.getWizard().markDirty('capacity');
      }
    }

    // TODO: Move to service, or don't
    function initializeCommand() {
      if (serverGroupCommand.viewState.imageId) {
        var foundImage = $scope.command.backingData.packageImages.filter(function(image) {
          return image.amis[serverGroupCommand.region] && image.amis[serverGroupCommand.region].indexOf(serverGroupCommand.viewState.imageId) !== -1;
        });
        if (foundImage.length) {
          serverGroupCommand.amiName = foundImage[0].imageName;
        }
      }
    }

    $scope.taskMonitor.onApplicationRefresh = function handleApplicationRefreshComplete() {
      $scope.taskMonitor.task.getCompletedKatoTask().then(function(katoTask) {
        if (katoTask.resultObjects && katoTask.resultObjects.length && katoTask.resultObjects[0].serverGroupNames) {
          var newStateParams = {
            serverGroup: katoTask.resultObjects[0].serverGroupNames[0].split(':')[1],
            accountId: $scope.command.credentials,
            region: $scope.command.region
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
      });
    };

    this.isValid = function () {
      return $scope.command &&
        ($scope.command.viewState.useAllImageSelection ? $scope.command.viewState.allImageSelection !== null : $scope.command.amiName !== null) &&
        ($scope.command.application !== null) &&
        ($scope.command.credentials !== null) && ($scope.command.instanceType !== null) &&
        ($scope.command.region !== null) && ($scope.command.availabilityZones !== null) &&
        ($scope.command.capacity.min !== null) && ($scope.command.capacity.max !== null) &&
        ($scope.command.capacity.desired !== null) &&
        modalWizardService.getWizard().isComplete();
    };

    this.showSubmitButton = function () {
      return modalWizardService.getWizard().allPagesVisited();
    };

    this.submit = function () {
      if ($scope.command.viewState.returnCommand) {
        $modalInstance.close($scope.command);
      } else {
        $scope.taskMonitor.submit(
          function() {
            return orcaService.cloneServerGroup($scope.command, application.name);
          }
        );
      }
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
