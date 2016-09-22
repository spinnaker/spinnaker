'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.aws.serverGroup.details.scalingPolicy.popover.component', [
    require('../chart/metricAlarmChart.component.js'),
  ])
  .component('awsScalingPolicyPopover', {
    bindings: {
      policy: '=',
      serverGroup: '=',
    },
    templateUrl: require('./scalingPolicyPopover.component.html'),
    controller: function() {

      this.$onInit = () => {
        this.alarm = this.policy.alarms[0];

        let showWait = false;
        if (this.policy.cooldown) {
          showWait = true;
        }
        if (this.policy.stepAdjustments && this.policy.stepAdjustments.length) {
          showWait = this.policy.stepAdjustments[0].operator !== 'decrease';
        }
        this.showWait = showWait;
      };
    }
  });
