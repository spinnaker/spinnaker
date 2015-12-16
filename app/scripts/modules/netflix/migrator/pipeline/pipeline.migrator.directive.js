'use strict';

require('../migrator.less');

let angular = require('angular');

module.exports = angular
  .module('spinnaker.migrator.pipeline.directive', [
    require('angular-ui-bootstrap'),
    require('../../../amazon/vpc/vpc.read.service.js'),
    require('../../../amazon/subnet/subnet.read.service.js'),
    require('../../../core/config/settings.js'),
    require('../migrator.service.js'),
    require('../../../core/utils/lodash.js'),
    require('../../../core/presentation/autoScroll/autoScroll.directive.js'),
    require('../../../core/pipeline/config/services/pipelineConfigService.js'),
    require('../../../core/utils/scrollTo/scrollTo.service.js'),
    require('../../../core/cache/cacheInitializer.js'),
  ])
  .directive('pipelineMigrator', function () {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        application: '=',
        pipeline: '=',
      },
      templateUrl: require('./pipeline.migrator.directive.html'),
      controller: 'PipelineMigratorActionCtrl',
      controllerAs: 'migratorActionCtrl',
    };
  })
  .controller('PipelineMigratorActionCtrl', function ($scope, $uibModal, vpcReader, settings, subnetReader, _) {

    $scope.showAction = false;

    $scope.submittingTemplateUrl = require('../migrator.modal.submitting.html');

    var subnets,
        actionableDeployStages = [];

    function testCluster(stage) {
      return function(cluster) {
        var region = cluster.availabilityZones ? Object.keys(cluster.availabilityZones)[0] : null;
        var account = cluster.account;
        var subnetType = cluster.subnetType;
        if (subnetType) {
          var subnetMatches = _(subnets)
            .filter({account: account, region: region, purpose: subnetType})
            .reject({target: 'elb'})
            .valueOf();
          if (subnetMatches.length) {
            return vpcReader.getVpcName(subnetMatches[0].vpcId).then(function (vpc) {
              if (vpc === 'Main') {
                $scope.showAction = true;
                if (cluster.strategy === 'highlander' || cluster.strategy === 'redblack') {
                  actionableDeployStages.push({strategy: cluster.strategy, name: stage.name});
                }
              }
            });
          }
        }
      };
    }

    if (settings.feature.vpcMigrator) {
      subnetReader.listSubnets().then(function (loadedSubnets) {
        subnets = loadedSubnets;
        var stages = $scope.pipeline.stages || [];
        stages.forEach(function (stage) {
          if (stage.type === 'deploy') {
            var clusters = stage.clusters || [];
            clusters.forEach(testCluster(stage));
          }
          if (stage.type === 'canary') {
            var clusterPairs = stage.clusterPairs || [];
            clusterPairs.forEach(function (clusterPair) {
              testCluster(stage)(clusterPair.baseline);
              testCluster(stage)(clusterPair.canary);
            });
          }
        });
      });
    }

    this.previewMigration = function () {
      $uibModal.open({
        templateUrl: require('./pipeline.migrator.modal.html'),
        controller: 'PipelineMigratorCtrl as ctrl',
        resolve: {
          pipeline: function () {
            return $scope.pipeline;
          },
          application: function () {
            return $scope.application;
          },
          type: function() {
            return $scope.type;
          },
          actionableDeployStages: function() {
            return actionableDeployStages;
          },
        }
      });
    };
  })
  .controller('PipelineMigratorCtrl', function ($scope, pipeline, application, type, actionableDeployStages,
                                                $modalInstance,
                                                migratorService, pipelineConfigService, scrollToService,
                                                cacheInitializer) {

    $scope.application = application;
    $scope.pipeline = pipeline;
    $scope.actionableDeployStages = actionableDeployStages;

    $scope.viewState = {
      computing: true,
    };

    var source = { pipelineId: pipeline.id, vpcName: 'Main', },
        target = { vpcName: 'vpc0', };


    var migrationConfig = {
      application: application,
      type: 'deepCopyPipeline',
      name: pipeline.name,
      source: source,
      target: target,
      dryRun: true,
    };

    var dryRun = migratorService.executeMigration(migrationConfig);

    dryRun.deferred.promise.then(
      function () {
        $scope.viewState.computing = false;
        $scope.preview = dryRun.executionPlan;
      },
      function (error) {
        $scope.viewState.computing = false;
        $scope.viewState.error = error;
      }
    );

    this.cancel = function () {
      dryRun.deferred.promise.cancelled = true;
      if ($scope.executor) {
        $scope.executor.deferred.promise.cancelled = true;
      }
      $modalInstance.dismiss();
    };

    this.submit = function () {
      $scope.viewState.executing = true;
      migrationConfig.dryRun = false;
      var executor = migratorService.executeMigration(migrationConfig);
      executor.deferred.promise.then(
        function () {
          $scope.viewState.executing = false;
          $scope.viewState.migrationComplete = true;
          reinitialize();
        },
        function (error) {
          $scope.viewState.executing = false;
          $scope.viewState.error = error;
        }
      );
      $scope.executor = executor;
    };

    function reinitialize() {
      pipelineConfigService.getPipelinesForApplication(application.name).then(function (pipelines) {
        application.pipelines = pipelines;
        // TODO: pipeline ID is not yet populated in the task result from Tide, so build it out based on name
        var newPipelines = pipelines.filter(function(test) {
          return test.name.indexOf(pipeline.name + ' - vpc0') === 0;
        });
        if (newPipelines && newPipelines.length) {
          scrollToService.scrollTo('pipeline-config-' + newPipelines[0].id, '.execution-groups', 180);
        }
        if ($scope.preview.securityGroups && $scope.preview.securityGroups.length) {
          cacheInitializer.refreshCache('securityGroups');
        }
        if ($scope.preview.loadBalancers && $scope.preview.loadBalancers.length) {
          cacheInitializer.refreshCache('loadBalancers');
        }
      });
    }
  });
