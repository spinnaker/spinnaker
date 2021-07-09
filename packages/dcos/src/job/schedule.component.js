'use strict';

import { module } from 'angular';

export const DCOS_JOB_SCHEDULE_COMPONENT = 'spinnaker.dcos.schedule.component';
export const name = DCOS_JOB_SCHEDULE_COMPONENT; // for backwards compatibility
module(DCOS_JOB_SCHEDULE_COMPONENT, []).component('dcosSchedule', {
  bindings: {
    schedule: '=',
  },
  templateUrl: require('./schedule.component.html'),
  controller: function () {
    if (this.schedule === undefined || this.schedule == null) {
      this.schedule = {};
    }
  },
});
