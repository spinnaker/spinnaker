'use strict';

require('../migrator.less');

let angular = require('angular');

module.exports = angular
  .module('spinnaker.migrator.directive', [
    require('angular-ui-bootstrap'),
    require('../../../amazon/vpc/vpc.read.service.js'),
    require('../../../core/config/settings.js'),
    require('../migrator.service.js'),
    require('../../../core/presentation/autoScroll/autoScroll.directive.js'),
  ])
  .directive('migrator', function () {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        application: '=',
        serverGroup: '=',
      },
      templateUrl: require('./serverGroup.migrator.directive.html'),
      controller: 'MigratorActionCtrl',
      controllerAs: 'migratorActionCtrl',
    };
  })
  .controller('MigratorActionCtrl', function ($scope, $uibModal, vpcReader, settings) {

    vpcReader.getVpcName($scope.serverGroup.vpcId).then(function (name) {
      $scope.showAction = name === 'Main' && settings.feature.vpcMigrator;
    });

    this.previewMigration = function () {
      $uibModal.open({
        templateUrl: require('./serverGroup.migrator.modal.html'),
        controller: 'MigratorCtrl as ctrl',
        resolve: {
          serverGroup: function () {
            return $scope.serverGroup;
          },
          application: function () {
            return $scope.application;
          },
          type: function() {
            return $scope.type;
          }
        }
      });
    };
  })
  .controller('MigratorCtrl', function ($scope, serverGroup, application, type, $modalInstance, migratorService) {

    $scope.submittingTemplateUrl = require('../migrator.modal.submitting.html');

    $scope.application = application;
    $scope.serverGroup = serverGroup;

    $scope.viewState = {
      computing: true,
    };

    $scope.source = {
      region: serverGroup.region,
      account: serverGroup.account
    };

    $scope.source.asgName = serverGroup.name;

    var target = {
      region: serverGroup.region,
      account: serverGroup.account,
      vpcName: 'vpc0'
    };

    var migrationConfig = {
      application: application,
      type: 'deepCopyServerGroup',
      name: serverGroup.name,
      source: $scope.source,
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
        },
        function (error) {
          $scope.viewState.executing = false;
          $scope.viewState.error = error;
        }
      );
      $scope.executor = executor;
    };

  });
