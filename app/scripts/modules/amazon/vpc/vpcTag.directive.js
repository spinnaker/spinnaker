'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.vpc.tag.directive', [
  require('./vpc.read.service.js'),
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
