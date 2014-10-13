'use strict';


angular.module('deckApp')
  .controller('SecurityGroupCtrl', function($scope, $state, notifications, securityGroup, application, securityGroupService, $modal) {

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
      $scope.securityGroup = securityGroupService.getApplicationSecurityGroup(application, securityGroup.account, securityGroup.region, securityGroup.name);
      if (!$scope.securityGroup) {
        $state.go('^');
        notifications.create({
          message: 'No security group named "' + securityGroup.name + '" was found in ' + securityGroup.account + ':' + securityGroup.region,
          autoDismiss: true,
          hideTimestamp: true,
          strong: true
        });
      }
    }

    extractSecurityGroup();

    application.registerAutoRefreshHandler(extractSecurityGroup, $scope);

    this.editInboundRules = function editInboundRules() {
      $modal.open({
        templateUrl: 'views/application/modal/securityGroup/editSecurityGroup.html',
        controller: 'EditSecurityGroupCtrl as ctrl',
        resolve: {
          securityGroup: function() {
            return securityGroupService.getSecurityGroupDetails(application, securityGroup.account, securityGroup.region, securityGroup.name);
          },
          applicationName: function() { return application.name; }
        }
      });
    };
  }
);
