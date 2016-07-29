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

        angular
          .element(element)
          .closest('.ui-select-match-item')
          .after(compiledTemplate);
      }
    };
  });
