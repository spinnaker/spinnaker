'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.backendServiceSelector.component', [])
  .directive('gceBackendServiceSelectorGenerator', function($compile) {
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
  })
  .component('gceBackendServiceSelector', {
    bindings: {
      command: '=',
      loadBalancerName: '='
    },
    templateUrl: require('./backendServiceSelector.component.html'),
    controller: function($scope) {

      $scope.$on('$destroy', () => {
        delete this.command.backendServices[this.loadBalancerName];
      });

      $scope.$on('uis:select', function(event) {
        event.preventDefault();
      });
    }
  });
