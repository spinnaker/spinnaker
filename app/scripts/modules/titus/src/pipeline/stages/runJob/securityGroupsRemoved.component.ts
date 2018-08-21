import { module } from 'angular';
import { has } from 'lodash';

export const SECURITY_GROUPS_REMOVED = 'spinnaker.titus.pipeline.stages.runJob.securityGroups.removed';
module(SECURITY_GROUPS_REMOVED, []).component('serverGroupSecurityGroupsRemoved', {
  templateUrl: require('./securityGroupsRemoved.component.html'),
  bindings: {
    command: '=',
    removed: '=',
  },
  controller() {
    this.acknowledgeSecurityGroupRemoval = () => {
      if (has(this.command, 'viewState.dirty')) {
        this.command.viewState.dirty.securityGroups = null;
      }
      if (this.removed && this.removed.length) {
        this.removed.length = 0;
      }
    };
  },
});
