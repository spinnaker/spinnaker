'use strict';

const angular = require('angular');

import { CONFIRMATION_MODAL_SERVICE, TASK_EXECUTOR } from '@spinnaker/core';

import { SCALING_POLICY_WRITE_SERVICE } from '@spinnaker/amazon';

import './scalingPolicySummary.component.less';

module.exports = angular.module('spinnaker.titus.serverGroup.details.scalingPolicy.alarmBasedSummary.component', [
  require('./upsert/upsertScalingPolicy.controller').name,
  CONFIRMATION_MODAL_SERVICE,
  SCALING_POLICY_WRITE_SERVICE,
  TASK_EXECUTOR,
])
  .component('titusAlarmBasedSummary', {
      bindings: {
        policy: '=',
        serverGroup: '=',
        application: '=',
      },
      templateUrl: require('./alarmBasedSummary.component.html'),
      controller: function($uibModal, scalingPolicyWriter, confirmationModalService, accountService, taskExecutor) {

        this.$onInit = () => {
          accountService.getAccountDetails(this.serverGroup.account).then(details => {
            // alarmServerGroup is used to trick the chart rendering into using AWS metrics
            this.alarmServerGroup = {
              type: 'aws',
              name: this.serverGroup.name,
              account: details.awsAccount,
              region: this.serverGroup.region,
            };
          });
        };

        this.popoverTemplate = require('./popover/scalingPolicyDetails.popover.html');

        this.editPolicy = () => {
          $uibModal.open({
            templateUrl: require('./upsert/upsertScalingPolicy.modal.html'),
            controller: 'titusUpsertScalingPolicyCtrl',
            controllerAs: 'ctrl',
            size: 'lg',
            resolve: {
              policy: () => this.policy,
              alarmServerGroup: () => this.alarmServerGroup,
              serverGroup: () => this.serverGroup,
              application: () => this.application,
            }
          });
        };

        this.deletePolicy = () => {
          const { application, policy, serverGroup } = this;
          const taskMonitor = {
            application,
            title: 'Deleting scaling policy ' + policy.id,
          };

          const submitMethod = () => taskExecutor.executeTask({
            application,
            description: 'Delete scaling policy ' + policy.id,
            job: [
              {
                type: 'deleteScalingPolicy',
                cloudProvider: 'titus',
                credentials: serverGroup.account,
                region: serverGroup.region,
                scalingPolicyID: policy.id,
                serverGroupName: serverGroup.name,
              }
            ]
          });

          confirmationModalService.confirm({
            header: `Really delete ${policy.id}?`,
            buttonText: 'Delete scaling policy',
            account: serverGroup.account,
            provider: 'titus',
            taskMonitorConfig: taskMonitor,
            submitMethod: submitMethod
          });
        };
      }
  });
