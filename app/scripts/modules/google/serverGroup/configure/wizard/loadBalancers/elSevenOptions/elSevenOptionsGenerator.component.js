'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.elSevenOptionsGenerator.component', [
    require('./backendServiceSelector.component.js')
  ])
  .directive('gceElSevenOptionsGenerator', function($compile) {
    let template = `<gce-backend-service-selector load-balancer-name="loadBalancerName" command="command">
                    </gce-backend-service-selector>`;

    return {
      restrict: 'E',
      scope: {
        command: '=',
        loadBalancerName: '@'
      },
      link: function(scope, element) {
        let compiledTemplate = $compile(template)(scope);

        // Look up DOM to find container for selected load balancer.
        let listItem = angular
          .element(element)
          .closest('.ui-select-match-item');

        // Drop service selector in between load balancers.
        listItem.after(compiledTemplate);

        scope.$on('$destroy', () => {
          // Remove selector if load balancer is removed.
          listItem.next().remove();
        });
      }
    };
  });
