'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.labels.component', []).component('dcosLabels', {
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
