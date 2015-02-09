'use strict';


angular.module('deckApp.securityGroup.single.controller', [
  'ui.router',
  'ui.bootstrap',
  'deckApp.notifications.service',
  'deckApp.securityGroup.read.service',
])
  .controller('SecurityGroupCtrl', function($scope, $state, notificationsService, securityGroup, application, securityGroupReader, $modal) {

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
        notificationsService.create({
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
