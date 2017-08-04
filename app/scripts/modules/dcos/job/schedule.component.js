'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.schedule.component', [])
  .component('dcosSchedule', {
    bindings: {
      schedule: '=',
    },
    templateUrl: require('./schedule.component.html'),
    controller: function () {
      if (this.schedule === undefined || this.schedule == null) {
        this.schedule = {};
      }
    }
  });
