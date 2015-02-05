'use strict';

angular.module('deckApp.pipelines.stage.deploy')
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Deploy',
      description: 'Deploys the previously baked AMI',
      key: 'deploy',
      templateUrl: 'scripts/modules/pipelines/config/stages/deploy/deployStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/deploy/deployExecutionDetails.html',
      controller: 'DeployStageCtrl',
      controllerAs: 'deployStageCtrl',
      validators: [
        {
          type: 'stageBeforeType',
          stageType: 'bake',
          message: 'You must have a bake stage immediately before any deploy stage.'
        },
        {
          type: 'stageBeforeMethod',
          validate: function(stageBefore, thisStage) {
            // should be caught in stageBeforeType validation
            if (stageBefore.type !== 'bake') {
              return null;
            }

            var thisStageRegion = null,
                stageBeforeRegion = null;

            if (thisStage.cluster && thisStage.cluster.availabilityZones) {
              var thisStageRegions = Object.keys(thisStage.cluster.availabilityZones);
              if (thisStageRegions.length) {
                thisStageRegion = thisStageRegions[0];
              }
            }

            if (stageBefore) {
              stageBeforeRegion = stageBefore.region;
            }

            if (thisStageRegion === stageBeforeRegion) {
              return null;
            }
            return  'Bake stage region (' + stageBeforeRegion + ') must match the subsequent deploy stage region (' + thisStageRegion + '). The pipeline will not execute successfully with this configuration.';
          }
        }
      ],
    });
  })
  .controller('DeployStageCtrl', function ($scope, stage, viewState,
                                           awsServerGroupCommandBuilder, awsServerGroupConfigurationService, awsServerGroupTransformer, _) {
    $scope.stage = stage;

    function initializeCommand() {
      if (!$scope.stage.cluster || $scope.stage.uninitialized) {
        $scope.stage.uninitialized = true;
      } else {
        awsServerGroupCommandBuilder.buildServerGroupCommandFromPipeline($scope.application, stage.cluster, $scope.stage.account).then(function (command) {
          awsServerGroupConfigurationService.configureCommand({name: stage.application}, command).then(function () {
            command.credentialsChanged();
            command.regionChanged();
            awsServerGroupConfigurationService.configureSubnetPurposes(command);
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
      };
    }

    function processCommandUpdateResult(result) {
      if (result.dirty.loadBalancers) {
        $scope.viewState.sections.loadBalancers.dirty = result.dirty.loadBalancers;
      }
      if (result.dirty.securityGroups) {
        $scope.viewState.sections.securityGroups.dirty = result.dirty.securityGroups;
      }
      if (result.dirty.availabilityZones) {
        $scope.viewState.sections.capacityZones.dirty = true;
      }
    }

    function resetCapacityZonesFlag() {
      $scope.viewState.sections.capacityZones.dirty = false;
    }

    function clearOnIncrement(field) {
      return function(newVal, oldVal) {
        var newLength = newVal && newVal.length ? newVal.length : 0,
          oldLength = oldVal && oldVal.length ? oldVal.length : 0;

        if (newLength > oldLength) {
          $scope.viewState.sections[field].dirty = null;
        }
      };
    }

    function applyCommandToStage() {
      var stageCluster = awsServerGroupTransformer.convertServerGroupCommandToDeployConfiguration($scope.command);
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
      $scope.$watch('command.viewState.usePreferredZones', resetCapacityZonesFlag);
      $scope.$watch('command.availabilityZones', clearOnIncrement('capacityZones'));
      $scope.$watch('command.loadBalancers', clearOnIncrement('loadBalancers'));
      $scope.$watch('command.securityGroups', clearOnIncrement('securityGroups'));
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
