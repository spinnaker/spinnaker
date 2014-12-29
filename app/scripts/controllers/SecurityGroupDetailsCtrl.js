'use strict';


angular.module('deckApp')
  .controller('SecurityGroupDetailsCtrl', function ($scope, $state, notificationsService, securityGroup, application, securityGroupService, $modal) {

    $scope.state = {
      loading: true
    };

    function extractSecurityGroup() {
      securityGroupService.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.region, securityGroup.name).then(function (details) {
        $scope.state.loading = false;
        $scope.securityGroup = details;

        var restanularlessDetails = details.plain();
        if (_.isEmpty(restanularlessDetails)) {
          fourOhFour();
        }
      },
      function() {
        fourOhFour();
      });
    }

    function fourOhFour() {
      notificationsService.create({
        message: 'No security group named "' + securityGroup.name + '" was found in ' + securityGroup.accountId + ':' + securityGroup.region,
        autoDismiss: true,
        hideTimestamp: true,
        strong: true
      });
      $state.go('^');
    }

    extractSecurityGroup();

    application.registerAutoRefreshHandler(extractSecurityGroup, $scope);

    this.editInboundRules = function editInboundRules() {
      $modal.open({
        templateUrl: 'views/application/modal/securityGroup/editSecurityGroup.html',
        controller: 'EditSecurityGroupCtrl as ctrl',
        resolve: {
          securityGroup: function() {
            return angular.copy($scope.securityGroup.plain());
          },
          application: function() { return application; }
        }
      });
    };
  }
);
