'use strict';


angular.module('spinnaker')
  .directive('subnetSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/subnetSelectField.html',
      scope: {
        subnets: '=',
        component: '=',
        field: '@',
        region: '=',
        onChange: '&',
        labelColumns: '@',
        helpKey: '@',
        readOnly: '=',
      },
      link: function(scope) {

        function setSubnets() {
          var subnets = scope.subnets || [];
          scope.activeSubnets = subnets.filter(function(subnet) { return !subnet.deprecated; });
          scope.deprecatedSubnets = subnets.filter(function(subnet) { return subnet.deprecated; });
        }

        scope.$watch('subnets', setSubnets);
      }
    };
  }
);
