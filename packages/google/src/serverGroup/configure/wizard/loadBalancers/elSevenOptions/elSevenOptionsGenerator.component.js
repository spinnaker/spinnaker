import * as angular from 'angular';

import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_ELSEVENOPTIONS_BACKENDSERVICESELECTOR_COMPONENT } from './backendServiceSelector.component';

('use strict');

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_ELSEVENOPTIONS_ELSEVENOPTIONSGENERATOR_COMPONENT =
  'spinnaker.deck.gce.elSevenOptionsGenerator.component';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_ELSEVENOPTIONS_ELSEVENOPTIONSGENERATOR_COMPONENT; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_ELSEVENOPTIONS_ELSEVENOPTIONSGENERATOR_COMPONENT, [
    GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_ELSEVENOPTIONS_BACKENDSERVICESELECTOR_COMPONENT,
  ])
  .directive('gceElSevenOptionsGenerator', [
    '$compile',
    function ($compile) {
      const template = `<gce-backend-service-selector load-balancer-name="loadBalancerName" command="command">
                    </gce-backend-service-selector>`;

      return {
        restrict: 'E',
        scope: {
          command: '=',
          loadBalancerName: '@',
        },
        link: function (scope, element) {
          const compiledTemplate = $compile(template)(scope);

          // Look up DOM to find container for selected load balancer.
          const listItem = angular.element(element).closest('.ui-select-match-item');

          // Drop service selector in between load balancers.
          listItem.after(compiledTemplate);

          scope.$on('$destroy', () => {
            // Remove selector if load balancer is removed.
            listItem.next().remove();
          });
        },
      };
    },
  ]);
