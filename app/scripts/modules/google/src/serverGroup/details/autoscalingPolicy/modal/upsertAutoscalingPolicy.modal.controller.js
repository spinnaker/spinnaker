'use strict';

const angular = require('angular');

import { TaskMonitor } from '@spinnaker/core';

import './upsertAutoscalingPolicy.modal.less';

module.exports = angular
  .module('spinnaker.deck.gce.upsertAutoscalingPolicy.modal.controller', [
    require('google/autoscalingPolicy/autoscalingPolicy.write.service').name,
    require('google/autoscalingPolicy/components/basicSettings/basicSettings.component.js').name,
    require('google/autoscalingPolicy/components/metricSettings/metricSettings.component.js').name,
  ])
  .controller('gceUpsertAutoscalingPolicyModalCtrl', function(
    policy,
    application,
    serverGroup,
    $uibModalInstance,
    gceAutoscalingPolicyWriter,
  ) {
    [this.action, this.isNew] = policy ? ['Edit', false] : ['New', true];
    this.policy = _.cloneDeep(policy || {});

    this.cancel = $uibModalInstance.dismiss;

    this.taskMonitor = new TaskMonitor({
      application: application,
      title: `${this.action} scaling policy for ${serverGroup.name}`,
      modalInstance: $uibModalInstance,
    });

    this.save = () => {
      const submitMethod = () =>
        gceAutoscalingPolicyWriter.upsertAutoscalingPolicy(application, serverGroup, this.policy);

      this.taskMonitor.submit(submitMethod);
    };
  });
