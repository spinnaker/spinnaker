'use strict';

angular.module('deckApp.pipelines.stage.deploy')
  .directive('deployInitializer', function() {
    return {
      restrict: 'E',
      scope: {
        stage: '=',
        application: '=',
      },
      templateUrl: 'scripts/modules/pipelines/config/stages/deploy/deployInitializer.html',
      controller: 'DeployInitializerCtrl',
      controllerAs: 'deployInitializerCtrl'
    };
  })
  .controller('DeployInitializerCtrl', function($scope, serverGroupCommandBuilder, serverGroupReader, securityGroupReader, deploymentStrategyService, _) {
    var controller = this;

    var noTemplate = { label: 'None', serverGroup: null, cluster: null };
    $scope.command = {
      strategy: '',
      template: noTemplate,
    };

    $scope.templates = [ noTemplate ];

    var allClusters = _.groupBy($scope.application.serverGroups, function(serverGroup) {
      return [serverGroup.cluster, serverGroup.account, serverGroup.region].join(':');
    });

    _.forEach(allClusters, function(cluster) {
      var latest = _.sortBy(cluster, 'name').pop();
      $scope.templates.push({
        cluster: latest.cluster,
        label: latest.cluster + ' (' + latest.account + ' - ' + latest.region + ')',
        serverGroupName: latest.name,
        serverGroup: latest
      });
    });

    $scope.deploymentStrategies = deploymentStrategyService.listAvailableStrategies();

    function transformCommandToStage(command) {
      // this is awful but this is the world we live in
      var zones = command.availabilityZones;
      command.availabilityZones = {};
      command.availabilityZones[command.region] = zones;
      if (command.securityGroups) {
        var securityGroups = command.securityGroups.map(function (securityGroupId) {
          return securityGroupReader.getApplicationSecurityGroup($scope.application, command.credentials, command.region, securityGroupId).name;
        });
        command.securityGroups = securityGroups;
      }
      $scope.stage.cluster = command;
      $scope.stage.account = command.credentials;
      $scope.stage.cluster.strategy = $scope.command.strategy;

      delete command.credentials;
    }

    function clearTemplate() {
      serverGroupCommandBuilder.buildNewServerGroupCommand($scope.application).then(function(command) {
        transformCommandToStage(command);
      });
    }

    controller.selectTemplate = function (selection) {
      selection = selection || $scope.command.template;
      if (selection && selection.cluster && selection.serverGroup) {
        var latest = selection.serverGroup;
        serverGroupReader.getServerGroup($scope.application.name, latest.account, latest.region, latest.name).then(function (details) {
          angular.extend(details, latest);
          serverGroupCommandBuilder.buildServerGroupCommandFromExisting($scope.application, details).then(function (command) {
            command.instanceType = details.launchConfig.instanceType;
            transformCommandToStage(command);
          });
        });
      } else {
        clearTemplate();
      }
    };

    function updateStrategy() {
      controller.selectTemplate();
    }

    $scope.$watch('command.strategy', updateStrategy);

    controller.useTemplate = function() {
      if (!$scope.stage.cluster) {
        clearTemplate();
      }
      delete $scope.stage.uninitialized;
    };
  });
