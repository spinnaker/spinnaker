'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.deck.gce.autoscalingPolicy.basicSettings.component', [])
  .component('gceAutoscalingPolicyBasicSettings', {
    bindings: {
      policy: '=',
      updatePolicy: '<',
    },
    templateUrl: require('./basicSettings.component.html'),
    controller: function controller() {
      this.modes = ['ON', 'OFF', 'ONLY_UP'];
    },
  });
