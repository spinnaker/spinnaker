'use strict';

const angular = require('angular');

import { SETTINGS, TASK_MONITOR_BUILDER } from '@spinnaker/core';

module.exports = angular.module('spinnaker.aws.serverGroup.details.elasticIp.controller', [
  require('./elasticIp.write.service.js'),
  TASK_MONITOR_BUILDER,
])
  .controller('ElasticIpCtrl', function($scope, $uibModalInstance, elasticIpWriter, taskMonitorBuilder,
                                        application, serverGroup, elasticIp, onTaskComplete) {
    $scope.serverGroup = serverGroup;
    $scope.elasticIp = elasticIp;
    $scope.gateUrl = SETTINGS.gateUrl;

    $scope.verification = {};

    this.isValid = () => $scope.verification.verified;

    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: null, // populated by action
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
