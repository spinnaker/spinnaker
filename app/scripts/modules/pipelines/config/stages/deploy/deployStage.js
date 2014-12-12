'use strict';

angular.module('deckApp.pipelines.stage.deploy')
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Deploy',
      description: 'Deploys the previously baked AMI',
      key: 'deploy',
      controller: 'DeployStageCtrl',
      controllerAs: 'deployStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/deploy/deployStage.html',
      initializationConfig: {
        controller: 'CreateDeployStageCtrl as createDeployStageCtrl',
        templateUrl: 'scripts/modules/pipelines/config/stages/deploy/deployStageInit.html',
      }
    });
  })
  .controller('DeployStageCtrl', function ($scope, stage, viewState,
                                           awsServerGroupService, serverGroupConfigurationService, orcaService) {
    $scope.stage = stage;

    function initializeCommand() {
      if (!$scope.stage.cluster) {
        $scope.stage.uninitialized = true;
      } else {
        awsServerGroupService.buildServerGroupCommandFromPipeline($scope.application, stage.cluster, $scope.stage.account).then(function (command) {
          serverGroupConfigurationService.configureCommand({name: stage.application}, command).then(function () {
            command.credentialsChanged();
            command.regionChanged();
            serverGroupConfigurationService.configureSubnetPurposes(command);
            $scope.viewState.commandInitialized = true;
            $scope.command = command;
            initializeWatches();

          });
        });
      }
    }

    $scope.$watch('stage.uninitialized', initializeCommand);

    function createResultProcessor(method) {
      return function() {
        processCommandUpdateResult(method());
      }
    }

    function processCommandUpdateResult(result) {
      if (result.dirty.loadBalancers) {
        $scope.viewState.sections.loadBalancers.dirty = true;
      }
      if (result.dirty.securityGroups) {
        $scope.viewState.sections.securityGroups.dirty = true;
      }
      if (result.dirty.availabilityZones) {
        $scope.viewState.sections.capacityZones.dirty = true;
      }
    }

    function applyCommandToStage() {
      var stageCluster = orcaService.convertServerGroupCommandToDeployConfiguration($scope.command);
      $scope.stage.cluster = stageCluster;
      $scope.stage.account = stageCluster.credentials;
      delete stageCluster.credentials;
    }

    function initializeWatches() {
      // watches the command and syncs to the stage if it's changed
      $scope.$watch(function() {
        // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over, and the viewState,
        // which should not be considered
        var diff = _.defaults({viewState: [], backingData: []}, $scope.command);
        return angular.toJson(diff);
      }, applyCommandToStage);
      $scope.$watch('command.credentials', createResultProcessor($scope.command.credentialsChanged));
      $scope.$watch('command.region', createResultProcessor($scope.command.regionChanged));
      $scope.$watch('command.subnetType', createResultProcessor($scope.command.subnetChanged));
      $scope.$watch('command.viewState.usePreferredZones', createResultProcessor($scope.command.usePreferredZonesChanged));
    }

    $scope.viewState = {
      commandInitialized: false,
      sections: {
        basicSettings: {
          expanded: false,
          dirty: false,
        },
        loadBalancers: {
          expanded: false,
          dirty: false,
        },
        securityGroups: {
          expanded: false,
          dirty: false,
        },
        instanceType: {
          expanded: false,
          dirty: false,
        },
        capacityZones: {
          expanded: false,
          dirty: false,
        },
        advancedSettings: {
          expanded: false,
          dirty: false,
        }
      }
    };
  });
