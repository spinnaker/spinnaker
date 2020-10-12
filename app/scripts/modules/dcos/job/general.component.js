'use strict';

import { module } from 'angular';

export const DCOS_JOB_GENERAL_COMPONENT = 'spinnaker.dcos.general.component';
export const name = DCOS_JOB_GENERAL_COMPONENT; // for backwards compatibility
module(DCOS_JOB_GENERAL_COMPONENT, []).component('dcosGeneral', {
  bindings: {
    general: '=',
  },
  templateUrl: require('./general.component.html'),
  controller: function () {
    if (this.general === undefined || this.general == null) {
      this.general = {
        cpus: 0.01,
        gpus: 0.0,
        mem: 128,
        disk: 0,
      };
    }

    this.idPattern = {
      test: function (id) {
        const pattern = /^([a-z0-9]*(\${.+})*)*$/;
        return pattern.test(id);
      },
    };
  },
});
