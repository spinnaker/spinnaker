'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.aws.securityGroup.editSecurityGroups.modal.controller', [
  require('../../../../core/utils/lodash.js'),
  require('../../../../core/task/monitor/taskMonitor.module.js'),
  require('../../../../core/serverGroup/serverGroup.write.service'),
  require('../../../../core/securityGroup/securityGroup.read.service'),
  require('../../../../core/task/taskExecutor.js'),
])
  .controller('EditSecurityGroupsCtrl', function($scope, $uibModalInstance, taskMonitorService, taskExecutor, _,
                                                 serverGroupWriter, securityGroupReader,
                                                 application, serverGroup, securityGroups) {
    this.command = {
      securityGroups: securityGroups.slice(0).sort((a, b) => a.name.localeCompare(b.name))
    };

    this.state = {
      securityGroupsLoaded: false,
      submitting: false,
      verification: {},
    };

    this.isValid = () => this.state.verification.verified;

    securityGroupReader.getAllSecurityGroups().then(allGroups => {
      let account = serverGroup.account,
          region = serverGroup.region,
          vpcId = serverGroup.vpcId;
      this.availableSecurityGroups = _.get(allGroups, [account, 'aws', region].join('.'), [])
        .filter(group => group.vpcId === vpcId);
      this.state.securityGroupsLoaded = true;
    });

    this.serverGroup = serverGroup;

    this.submit = () => {
      var submitMethod = () => {
        this.state.submitting = true;
        return serverGroupWriter.updateSecurityGroups(serverGroup, this.command.securityGroups, application);
      };

      var taskMonitorConfig = {
        modalInstance: $uibModalInstance,
        application: application,
        title: 'Update Security Groups for ' + serverGroup.name,
        onTaskComplete: application.serverGroups.refresh,
      };

      this.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      this.taskMonitor.submit(submitMethod);
    };

    this.cancel = $uibModalInstance.dismiss;
  });
