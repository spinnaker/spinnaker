'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.general.component', []).component('dcosGeneral', {
  bindings: {
    general: '=',
  },
  templateUrl: require('./general.component.html'),
  controller: function() {
    if (this.general === undefined || this.general == null) {
      this.general = {
        cpus: 0.01,
        gpus: 0.0,
        mem: 128,
        disk: 0,
      };
    }

    this.idPattern = {
      test: function(id) {
        var pattern = /^([a-z0-9]*(\${.+})*)*$/;
        return pattern.test(id);
      },
    };
  },
});
