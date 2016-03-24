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
      this.alarm = this.policy.alarms[0];
    }
  });
