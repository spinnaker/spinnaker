'use strict';

const angular = require('angular');

import { TASK_MONITOR_BUILDER } from '@spinnaker/core';

import './upsertAutoscalingPolicy.modal.less';

module.exports = angular.module('spinnaker.deck.gce.upsertAutoscalingPolicy.modal.controller', [
    require('google/autoscalingPolicy/autoscalingPolicy.write.service'),
    require('google/autoscalingPolicy/components/basicSettings/basicSettings.component.js'),
    require('google/autoscalingPolicy/components/metricSettings/metricSettings.component.js'),
    TASK_MONITOR_BUILDER,
  ])
  .controller('gceUpsertAutoscalingPolicyModalCtrl', function (policy, application, serverGroup,
                                                               taskMonitorBuilder,
                                                               $uibModalInstance, gceAutoscalingPolicyWriter) {
    [ this.action, this.isNew ] = policy ? [ 'Edit', false ] : [ 'New', true ];
    this.policy = _.cloneDeep(policy || {});

    this.cancel = $uibModalInstance.dismiss;

    this.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: `${this.action} scaling policy for ${serverGroup.name}`,
      modalInstance: $uibModalInstance,
    });

    this.save = () => {
      let submitMethod = () => gceAutoscalingPolicyWriter.upsertAutoscalingPolicy(application, serverGroup, this.policy);

      this.taskMonitor.submit(submitMethod);
    };
  });
