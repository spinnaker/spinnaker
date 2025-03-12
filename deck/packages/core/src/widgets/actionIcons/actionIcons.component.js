'use strict';

import * as angular from 'angular';

export const CORE_WIDGETS_ACTIONICONS_ACTIONICONS_COMPONENT = 'spinnaker.core.actionIcons.component';
export const name = CORE_WIDGETS_ACTIONICONS_ACTIONICONS_COMPONENT; // for backwards compatibility
angular.module(CORE_WIDGETS_ACTIONICONS_ACTIONICONS_COMPONENT, []).component('actionIcons', {
  bindings: {
    edit: '&',
    editInfo: '@',
    destroy: '&',
    destroyInfo: '@',
  },
  templateUrl: require('./actionIcons.component.html'),
  controller: angular.noop,
});
