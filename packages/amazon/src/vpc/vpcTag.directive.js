'use strict';

import { module } from 'angular';

import { VpcReader } from '../vpc/VpcReader';

export const AMAZON_VPC_VPCTAG_DIRECTIVE = 'spinnaker.amazon.vpc.tag.directive';
export const name = AMAZON_VPC_VPCTAG_DIRECTIVE; // for backwards compatibility
module(AMAZON_VPC_VPCTAG_DIRECTIVE, []).directive('vpcTag', function () {
  return {
    restrict: 'E',
    scope: {
      vpcId: '=',
    },
    template: '<span class="vpc-tag">{{vpcLabel}}</span>',
    link: function (scope) {
      function applyLabel() {
        if (!scope.vpcId) {
          scope.vpcLabel = 'None (EC2 Classic)';
        } else {
          VpcReader.getVpcName(scope.vpcId).then(function (name) {
            scope.vpcLabel = '(' + scope.vpcId + ')';

            if (name) {
              scope.vpcLabel = name + ' ' + scope.vpcLabel;
            }
          });
        }
      }

      scope.$watch('vpcId', applyLabel, true);
    },
  };
});
