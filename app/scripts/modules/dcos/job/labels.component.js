'use strict';

const angular = require('angular');

export const DCOS_JOB_LABELS_COMPONENT = 'spinnaker.dcos.labels.component';
export const name = DCOS_JOB_LABELS_COMPONENT; // for backwards compatibility
angular.module(DCOS_JOB_LABELS_COMPONENT, []).component('dcosLabels', {
  bindings: {
    labels: '=',
  },
  templateUrl: require('./labels.component.html'),
  controller: function() {
    if (this.labels === undefined || this.labels == null) {
      this.labels = {};
    }
  },
});
