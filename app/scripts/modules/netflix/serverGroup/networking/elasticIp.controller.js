'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.serverGroup.details.elasticIp.controller', [
  require('./elasticIp.write.service.js'),
  require('../../../core/task/monitor/taskMonitorService.js')
])
  .controller('ElasticIpCtrl', function($scope, $uibModalInstance, elasticIpWriter, taskMonitorService,
                                        application, serverGroup, elasticIp, onTaskComplete, settings) {
    $scope.serverGroup = serverGroup;
    $scope.elasticIp = elasticIp;
    $scope.gateUrl = settings.gateUrl;

    $scope.verification = {};

    this.isValid = () => $scope.verification.verified;

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete
    });

    this.associate = function () {
      var submitMethod = function () {
        return elasticIpWriter.associateElasticIpWithCluster(
          application, serverGroup.account, serverGroup.cluster, serverGroup.region, $scope.elasticIp
        );
      };

      $scope.taskMonitor.title = 'Associating Elastic IP with ' + serverGroup.cluster;
      $scope.taskMonitor.submit(submitMethod);
    };

    this.disassociate = function () {
      var submitMethod = function() {
        return elasticIpWriter.disassociateElasticIpWithCluster(
          application, serverGroup.account, serverGroup.cluster, serverGroup.region, elasticIp.address
        );
      };

      $scope.taskMonitor.title = 'Disassociating Elastic IP with ' + serverGroup.cluster;
      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
