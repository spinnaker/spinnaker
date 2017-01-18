'use strict';

let angular = require('angular');

import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

require('./upsertAutoscalingPolicy.modal.less');

module.exports = angular.module('spinnaker.deck.gce.upsertAutoscalingPolicy.modal.controller', [
    require('../../../../autoscalingPolicy/autoscalingPolicy.write.service'),
    require('../../../../autoscalingPolicy/components/basicSettings/basicSettings.component.js'),
    require('../../../../autoscalingPolicy/components/metricSettings/metricSettings.component.js'),
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
