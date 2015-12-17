'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.details.elasticIp.controller', [
  require('../../../../core/account/account.service.js'),
  require('./elasticIp.write.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js')
])
  .controller('azureElasticIpCtrl', function($scope, $modalInstance, accountService, elasticIpWriter, taskMonitorService,
                                                application, serverGroup, elasticIp, onTaskComplete) {
    $scope.serverGroup = serverGroup;
    $scope.elasticIp = elasticIp;

    $scope.verification = {
      requireAccountEntry: accountService.challengeDestructiveActions(serverGroup.account)
    };

    this.isValid = function () {
      if ($scope.verification.requireAccountEntry && $scope.verification.verifyAccount !== serverGroup.account.toUpperCase()) {
        return false;
      }
      return true;
    };

    this.associate = function () {
      var taskMonitorConfig = {
        application: application,
        title: 'Associating Elastic IP with ' + serverGroup.cluster,
        hasKatoTask: false,
        modalInstance: $modalInstance,
        onTaskComplete: onTaskComplete
      };

      var submitMethod = function () {
        return elasticIpWriter.associateElasticIpWithCluster(
          application, serverGroup.account, serverGroup.cluster, serverGroup.region, $scope.elasticIp
        );
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);
      $scope.taskMonitor.submit(submitMethod);
    };

    this.disassociate = function () {
        var taskMonitorConfig = {
          application: application,
          title: 'Disassociating Elastic IP with ' + serverGroup.cluster,
          hasKatoTask: false,
          modalInstance: $modalInstance,
          onTaskComplete: onTaskComplete
        };

      var submitMethod = function() {
        return elasticIpWriter.disassociateElasticIpWithCluster(
          application, serverGroup.account, serverGroup.cluster, serverGroup.region, elasticIp.address
        );
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);
      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
