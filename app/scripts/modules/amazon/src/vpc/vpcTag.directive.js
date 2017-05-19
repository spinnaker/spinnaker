'use strict';

const angular = require('angular');

import { VPC_READ_SERVICE } from '../vpc/vpc.read.service';

module.exports = angular.module('spinnaker.vpc.tag.directive', [
  VPC_READ_SERVICE,
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
            vpcReader.getVpcName(scope.vpcId).then(function (name) {
              scope.vpcLabel = '(' + scope.vpcId + ')';

              if (name) {
                scope.vpcLabel = name + ' ' + scope.vpcLabel;
              }
            });
          }
        }

        scope.$watch('vpcId', applyLabel, true);
      }
    };
  });
