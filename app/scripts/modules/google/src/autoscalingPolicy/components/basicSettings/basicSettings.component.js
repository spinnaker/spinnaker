'use strict';

const angular = require('angular');

export const GOOGLE_AUTOSCALINGPOLICY_COMPONENTS_BASICSETTINGS_BASICSETTINGS_COMPONENT =
  'spinnaker.deck.gce.autoscalingPolicy.basicSettings.component';
export const name = GOOGLE_AUTOSCALINGPOLICY_COMPONENTS_BASICSETTINGS_BASICSETTINGS_COMPONENT; // for backwards compatibility
angular
  .module(GOOGLE_AUTOSCALINGPOLICY_COMPONENTS_BASICSETTINGS_BASICSETTINGS_COMPONENT, [])
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
