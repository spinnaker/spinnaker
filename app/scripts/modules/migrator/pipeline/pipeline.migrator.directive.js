'use strict';

let angular = require('angular');

require('./../migrator.modal.submitting.html');
require('./pipeline.migrator.directive.html');
require('./pipeline.migrator.modal.html');

module.exports = angular
  .module('spinnaker.migrator.pipeline.directive', [
    require('exports?"ui.bootstrap"!angular-bootstrap'),
    require('../../vpc/vpc.read.service.js'),
    require('../../subnet/subnet.read.service.js'),
    require('../../../settings/settings.js'),
    require('../migrator.service.js'),
    require('utils/lodash.js'),
    require('../../../directives/autoScroll.directive.js'),
    require('../../pipelines/config/services/pipelineConfigService.js'),
    require('utils/scrollTo/scrollTo.service.js'),
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
  .controller('PipelineMigratorActionCtrl', function ($scope, $modal, vpcReader, settings, subnetReader, _) {

    $scope.showAction = false;

    var subnets;

    function testCluster(cluster) {
      var region = cluster.availabilityZones ? Object.keys(cluster.availabilityZones)[0] : null;
      var account = cluster.account;
      var subnetType = cluster.subnetType;
      if (subnetType) {
        var subnetMatches = _(subnets)
          .filter({account: account, region: region, purpose: subnetType})
          .reject({target: 'elb'})
          .valueOf();
        if (subnetMatches.length) {
          vpcReader.getVpcName(subnetMatches[0].vpcId).then(function(vpc) {
            if (vpc === 'Main') {
              $scope.showAction = true;
            }
          });
        }
      }
      return false;
    }

    if (settings.feature.vpcMigrator) {
      subnetReader.listSubnets().then(function (loadedSubnets) {
        subnets = loadedSubnets;
        var stages = $scope.pipeline.stages || [];
        stages.forEach(function (stage) {
          if (stage.type === 'deploy') {
            var clusters = stage.clusters || [];
            clusters.forEach(function (cluster) {
              if (testCluster(cluster)) {
                $scope.showAction = true;
              }
            });
          }
          if (stage.type === 'canary') {
            var clusterPairs = stage.clusterPairs || [];
            clusterPairs.forEach(function (clusterPair) {
              if (testCluster(clusterPair.baseline)) {
                $scope.showAction = true;
              }
              if (testCluster(clusterPair.canary)) {
                $scope.showAction = true;
              }
            });
          }
        });
      });
    }

    this.previewMigration = function () {
      $modal.open({
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
        }
      });
    };
  })
  .controller('PipelineMigratorCtrl', function ($scope, pipeline, application, type, $modalInstance, migratorService, pipelineConfigService, scrollToService) {

    $scope.application = application;
    $scope.pipeline = pipeline;

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
      });
    }
  })
.name;
