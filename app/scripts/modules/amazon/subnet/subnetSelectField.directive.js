'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.subnet.subnetSelectField.directive', [
  require('../../core/config/settings'),
  require('../../core/utils/lodash'),
])
  .directive('subnetSelectField', function (settings, _) {
    return {
      restrict: 'E',
      templateUrl: require('./subnetSelectField.directive.html'),
      scope: {
        subnets: '=',
        component: '=',
        field: '@',
        region: '=',
        onChange: '&',
        labelColumns: '@',
        helpKey: '@',
        readOnly: '=',
        application: '='
      },
      link: function(scope) {

        scope.hideClassic = false;
        let lockoutDate = _.get(settings, 'providers.aws.classicLaunchLockout');
        if (lockoutDate) {
          let application = scope.application,
              createTs = Number(_.get(application, 'attributes.createTs', 0));
          if (createTs >= lockoutDate) {
            scope.hideClassic = true;
            if (!scope.component[scope.field] && scope.subnets && scope.subnets.length) {
              scope.component[scope.field] = scope.subnets[0].purpose;
              if (scope.onChange) {
                scope.onChange();
              }
            }
          }
        }
        function setSubnets() {
          var subnets = scope.subnets || [];
          scope.activeSubnets = subnets.filter(function(subnet) { return !subnet.deprecated; });
          scope.deprecatedSubnets = subnets.filter(function(subnet) { return subnet.deprecated; });
          if (scope.hideClassic && subnets.length) {
            if (!scope.component[scope.field] && subnets.length) {
              scope.component[scope.field] = subnets[0].purpose;
            }
          }
        }

        scope.$watch('subnets', setSubnets);
      }
    };
});
