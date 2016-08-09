'use strict';

let angular = require('angular');
require('./upsertAutoscalingPolicy.modal.less');

module.exports = angular.module('spinnaker.deck.gce.upsertAutoscalingPolicy.modal.controller', [
    require('../../../../autoscalingPolicy/autoscalingPolicy.write.service'),
    require('../../../../autoscalingPolicy/components/basicSettings/basicSettings.component.js'),
    require('../../../../autoscalingPolicy/components/metricSettings/metricSettings.component.js'),
    require('../../../../../core/task/monitor/taskMonitorService.js')
  ])
  .controller('gceUpsertAutoscalingPolicyModalCtrl', function (policy, application, serverGroup,
                                                               taskMonitorService,
                                                               $uibModalInstance, gceAutoscalingPolicyWriter) {
    [ this.action, this.isNew ] = policy ? [ 'Edit', false ] : [ 'New', true ];
    this.policy = _.cloneDeep(policy || {});

    this.cancel = $uibModalInstance.dismiss;

    this.save = () => {
      let submitMethod = () => gceAutoscalingPolicyWriter.upsertAutoscalingPolicy(application, serverGroup, this.policy);

      let taskMonitorConfig = {
        modalInstance: $uibModalInstance,
        application: application,
        title: `${this.action} scaling policy for ${serverGroup.name}`
      };

      this.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);
      this.taskMonitor.submit(submitMethod);
    };
  });
