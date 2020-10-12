'use strict';

import { module } from 'angular';

// TODO(dpeach): this approach is unmaintainable because we
// have to intercept ui-select event emitters to make it work.
export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_ELSEVENOPTIONS_BACKENDSERVICESELECTOR_COMPONENT =
  'spinnaker.deck.gce.backendServiceSelector.component';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_ELSEVENOPTIONS_BACKENDSERVICESELECTOR_COMPONENT; // for backwards compatibility
module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_ELSEVENOPTIONS_BACKENDSERVICESELECTOR_COMPONENT, []).component(
  'gceBackendServiceSelector',
  {
    bindings: {
      command: '=',
      loadBalancerName: '=',
    },
    templateUrl: require('./backendServiceSelector.component.html'),
    controller: [
      '$scope',
      function ($scope) {
        $scope.$on('$destroy', () => {
          if (this.command.backendServices) {
            delete this.command.backendServices[this.loadBalancerName];
          }
        });

        $scope.$on('uis:select', function (event) {
          event.preventDefault();
        });
      },
    ],
  },
);
