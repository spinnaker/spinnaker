'use strict';

import * as angular from 'angular';

export const CORE_APPLICATION_CONFIG_LINKS_EDITLINKS_MODAL_CONTROLLER =
  'spinnaker.core.application.config.links.editJson';
export const name = CORE_APPLICATION_CONFIG_LINKS_EDITLINKS_MODAL_CONTROLLER; // for backwards compatibility
angular.module(CORE_APPLICATION_CONFIG_LINKS_EDITLINKS_MODAL_CONTROLLER, []).controller('EditLinksModalCtrl', [
  'sections',
  '$uibModalInstance',
  function (sections, $uibModalInstance) {
    this.cancel = $uibModalInstance.dismiss;

    this.initialize = () => {
      this.command = angular.toJson(sections, 2);
    };

    this.update = () => {
      try {
        $uibModalInstance.close(JSON.parse(this.command));
      } catch (e) {
        this.invalid = true;
        this.errorMessage = e.message;
      }
    };

    this.initialize();
  },
]);
