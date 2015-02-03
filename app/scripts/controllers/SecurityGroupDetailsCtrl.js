'use strict';


angular.module('deckApp')
  .controller('SecurityGroupDetailsCtrl', function ($scope, $state, notificationsService, securityGroup, application,
                                                    confirmationModalService, securityGroupWriter, securityGroupService,
                                                    $modal) {

    $scope.state = {
      loading: true
    };

    function extractSecurityGroup() {
      securityGroupService.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.region, securityGroup.vpcId, securityGroup.name).then(function (details) {
        $scope.state.loading = false;
        $scope.securityGroup = details;

        var restangularlessDetails = details.plain();
        if (_.isEmpty(restangularlessDetails)) {
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

    this.deleteSecurityGroup = function deleteSecurityGroup() {
      var taskMonitor = {
        application: application,
        title: 'Deleting ' + securityGroup.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true
      };

      var submitMethod = function () {
        securityGroup.providerType = $scope.securityGroup.type;
        return securityGroupWriter.deleteSecurityGroup($scope.securityGroup, application);
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + securityGroup.name + '?',
        buttonText: 'Delete ' + securityGroup.name,
        destructive: true,
        account: securityGroup.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);
