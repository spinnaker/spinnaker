'use strict';

let angular = require('angular');

require('./scalingPolicySummary.component.less');

module.exports = angular.module('spinnaker.aws.instance.details.scalingPolicy.directive', [
  require('./popover/scalingPolicyPopover.component.js'),
])
  .component('scalingPolicySummary', {
      bindings: {
        policy: '=',
        serverGroup: '=',
        application: '=',
      },
      templateUrl: require('./scalingPolicySummary.component.html'),
      controller: function() {
        this.popoverTemplate = require('./popover/scalingPolicyDetails.popover.html');
      }
  });
