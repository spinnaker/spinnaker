angular.module('deckApp.pipelines')
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
    }
  })
  .controller('DeployInitializerCtrl', function($scope, serverGroupService, securityGroupService) {
    $scope.templates = [
      { label: '[None]', serverGroup: null, cluster: null }
    ].concat($scope.application.clusters.map(function(cluster) {
        var latest = _.sortBy(cluster.serverGroups, 'name').pop();
        return {
          cluster: cluster,
          label: cluster.name + ' (' + latest.account + ' - ' + latest.region + ')',
          serverGroup: latest.name
        };
      }));

    function clearTemplate() {
      $scope.stage.cluster = { application: $scope.application.name };
    }

    this.selectTemplate = function (selection) {
      if (selection.cluster && selection.cluster.serverGroups) {
        var cluster = selection.cluster;
        var serverGroups = _.sortBy(cluster.serverGroups, 'name'),
          latest = serverGroups.pop();
        serverGroupService.getServerGroup($scope.application.name, latest.account, latest.region, latest.name).then(function (details) {
          angular.extend(details, latest);
          serverGroupService.buildServerGroupCommandFromExisting($scope.application, details).then(function (command) {
            command.instanceType = details.launchConfig.instanceType;
            // this is awful but this is the world we live in
            var zones = command.availabilityZones;
            command.availabilityZones = {};
            command.availabilityZones[command.region] = zones;
            var securityGroups = command.securityGroups.map(function (securityGroupId) {
              return securityGroupService.getApplicationSecurityGroup($scope.application, command.credentials, command.region, securityGroupId).name
            });
            command.securityGroups = securityGroups;

            $scope.stage.cluster = command;
            $scope.stage.account = command.credentials;

            delete command.credentials;
          });
        });
      } else {
        clearTemplate();
      }
    };

    this.useTemplate = function() {
      if (!$scope.stage.cluster) {
        clearTemplate();
      }
      delete $scope.stage.uninitialized;
    };
  });
