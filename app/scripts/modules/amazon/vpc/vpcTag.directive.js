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
      template: `
        <span ng-if="!vpcId" class="loader"></span>
        <span ng-if="vpcId" class="vpc-tag">{{vpcLabel}}</span>
        `,
      link: function(scope) {
        function applyLabel() {
          if (scope.vpcId) {
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
