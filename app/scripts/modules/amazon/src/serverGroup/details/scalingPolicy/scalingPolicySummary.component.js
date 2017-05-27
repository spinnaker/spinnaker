'use strict';

const angular = require('angular');

import { CONFIRMATION_MODAL_SERVICE } from '@spinnaker/core';

import { SCALING_POLICY_POPOVER } from './popover/scalingPolicyPopover.component';

import './scalingPolicySummary.component.less';

module.exports = angular.module('spinnaker.amazon.serverGroup.details.scalingPolicy.component', [
  require('./scalingPolicy.write.service.js'),
  require('./upsert/upsertScalingPolicy.controller.js'),
  SCALING_POLICY_POPOVER,
  CONFIRMATION_MODAL_SERVICE,
])
  .component('scalingPolicySummary', {
      bindings: {
        policy: '=',
        serverGroup: '=',
        application: '=',
      },
      templateUrl: require('./scalingPolicySummary.component.html'),
      controller: function($uibModal, scalingPolicyWriter, confirmationModalService) {
        this.popoverTemplate = require('./popover/scalingPolicyDetails.popover.html');

        this.editPolicy = () => {
          $uibModal.open({
            templateUrl: require('./upsert/upsertScalingPolicy.modal.html'),
            controller: 'awsUpsertScalingPolicyCtrl',
            controllerAs: 'ctrl',
            size: 'lg',
            resolve: {
              policy: () => this.policy,
              serverGroup: () => this.serverGroup,
              application: () => this.application,
            }
          });
        };

        this.deletePolicy = () => {
          var taskMonitor = {
            application: this.application,
            title: 'Deleting scaling policy ' + this.policy.policyName,
          };

          var submitMethod = () => scalingPolicyWriter.deleteScalingPolicy(this.application, this.serverGroup, this.policy);

          confirmationModalService.confirm({
            header: 'Really delete ' + this.policy.policyName + '?',
            buttonText: 'Delete scaling policy',
            account: this.serverGroup.account,
            provider: 'aws',
            taskMonitorConfig: taskMonitor,
            submitMethod: submitMethod
          });
        };
      }
  });
