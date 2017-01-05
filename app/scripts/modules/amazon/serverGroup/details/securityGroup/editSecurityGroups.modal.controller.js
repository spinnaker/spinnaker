'use strict';

import _ from 'lodash';
let angular = require('angular');

import {TASK_EXECUTOR} from 'core/task/taskExecutor';
import {SERVER_GROUP_WRITER_SERVICE} from 'core/serverGroup/serverGroupWriter.service';

module.exports = angular.module('spinnaker.serverGroup.details.aws.securityGroup.editSecurityGroups.modal.controller', [
  require('core/task/monitor/taskMonitor.module.js'),
  SERVER_GROUP_WRITER_SERVICE,
  require('core/securityGroup/securityGroup.read.service'),
  TASK_EXECUTOR,
])
  .controller('EditSecurityGroupsCtrl', function($scope, $uibModalInstance, taskMonitorService, taskExecutor,
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

    this.taskMonitor = taskMonitorService.buildTaskMonitor({
      modalInstance: $uibModalInstance,
      application: application,
      title: 'Update Security Groups for ' + serverGroup.name,
      onTaskComplete: () => application.serverGroups.refresh(),
    });

    this.submit = () => {
      var submitMethod = () => {
        this.state.submitting = true;
        return serverGroupWriter.updateSecurityGroups(serverGroup, this.command.securityGroups, application);
      };

      this.taskMonitor.submit(submitMethod);
    };

    this.cancel = $uibModalInstance.dismiss;
  });
