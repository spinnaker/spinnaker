import { module } from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

import { GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_MODAL_UPSERTAUTOSCALINGPOLICY_MODAL_CONTROLLER } from './modal/upsertAutoscalingPolicy.modal.controller';

('use strict');

export const GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_ADDAUTOSCALINGPOLICYBUTTON_COMPONENT =
  'spinnaker.gce.serverGroup.details.scalingPolicy.addButton';
export const name = GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_ADDAUTOSCALINGPOLICYBUTTON_COMPONENT; // for backwards compatibility
module(GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_ADDAUTOSCALINGPOLICYBUTTON_COMPONENT, [
  ANGULAR_UI_BOOTSTRAP,
  GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_MODAL_UPSERTAUTOSCALINGPOLICY_MODAL_CONTROLLER,
]).component('gceAddAutoscalingPolicyButton', {
  bindings: {
    serverGroup: '=',
    application: '=',
  },
  template: '<a href ng-click="$ctrl.addAutoscalingPolicy()">Create new scaling policy</a>',
  controller: [
    '$uibModal',
    function ($uibModal) {
      this.addAutoscalingPolicy = () => {
        $uibModal.open({
          templateUrl: require('./modal/upsertAutoscalingPolicy.modal.html'),
          controller: 'gceUpsertAutoscalingPolicyModalCtrl',
          controllerAs: 'ctrl',
          size: 'lg',
          resolve: {
            policy: () => undefined,
            serverGroup: () => this.serverGroup,
            application: () => this.application,
          },
        });
      };
    },
  ],
});
