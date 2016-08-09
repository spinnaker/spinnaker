'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.autoscalingPolicy.basicSettings.component', [])
  .component('gceAutoscalingPolicyBasicSettings', {
    bindings: {
      policy: '='
    },
    templateUrl: require('./basicSettings.component.html')
  });
