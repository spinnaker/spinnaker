'use strict';


angular.module('spinnaker.securityGroup.single.controller', [
  'ui.router',
  'ui.bootstrap',
  'spinnaker.securityGroup.read.service',
])
  .controller('SecurityGroupCtrl', function($scope, $state, securityGroup, application, securityGroupReader, $modal) {

    $scope.displayOptions = {
      showServerGroups: true,
      showLoadBalancers: true
    };

    $scope.sortFilter = {
      allowSorting: false
    };

    $scope.account = securityGroup.account;
    $scope.region = securityGroup.region;

    function extractSecurityGroup() {
      $scope.securityGroup = securityGroupReader.getApplicationSecurityGroup(application, securityGroup.account, securityGroup.region, securityGroup.name);
      if (!$scope.securityGroup) {
        $state.go('^');
      }
    }

    extractSecurityGroup();

    application.registerAutoRefreshHandler(extractSecurityGroup, $scope);

    this.editInboundRules = function editInboundRules() {
      $modal.open({
        templateUrl: 'scripts/modules/securityGroups/configure/aws/editSecurityGroup.html',
        controller: 'EditSecurityGroupCtrl as ctrl',
        resolve: {
          securityGroup: function() {
            return securityGroupReader.getSecurityGroupDetails(application, securityGroup.account, securityGroup.region, securityGroup.name);
          },
          applicationName: function() { return application.name; }
        }
      });
    };
  }
);
