'use strict';

angular.module('spinnaker.vpc.tag.directive', [
  'spinnaker.vpc.read.service',
])
  .directive('vpcTag', function(vpcReader) {
    return {
      restrict: 'E',
      scope: {
        vpcId: '=',
      },
      template: '<span class="vpc-tag">{{vpcLabel}}</span>',
      link: function(scope) {
        function applyLabel() {
          if (!scope.vpcId) {
            scope.vpcLabel = 'None (EC2 Classic)';
          } else {
            vpcReader.listVpcs().then(function (vpcs) {
              var vpc = _.find(vpcs, {id: scope.vpcId});
              scope.vpcLabel = '(' + scope.vpcId + ')';

              if (vpc) {
                scope.vpcLabel = vpc.name + ' ' + scope.vpcLabel;
              }
            });
          }
        }

        scope.$watch('vpcId', applyLabel, true);
      }
    };
  });
