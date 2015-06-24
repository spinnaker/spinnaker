'use strict';


angular.module('spinnaker.securityGroup.aws.details.controller', [
  'ui.router',
  'ui.bootstrap',
  'spinnaker.securityGroup.read.service',
  'spinnaker.securityGroup.write.service',
  'spinnaker.confirmationModal.service'
])
  .controller('awsSecurityGroupDetailsCtrl', function ($scope, $state, securityGroup, application,
                                                    confirmationModalService, securityGroupWriter, securityGroupReader,
                                                    $modal) {

    $scope.state = {
      loading: true
    };

    function extractSecurityGroup() {
      securityGroupReader.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.region, securityGroup.vpcId, securityGroup.name).then(function (details) {
        $scope.state.loading = false;

        if (!details || _.isEmpty( details.plain())) {
          fourOhFour();
        } else {
          $scope.securityGroup = details;
        }
      },
      function() {
        fourOhFour();
      });
    }

    function fourOhFour() {
      $state.go('^');
    }

    extractSecurityGroup();

    application.registerAutoRefreshHandler(extractSecurityGroup, $scope);

    this.editInboundRules = function editInboundRules() {
      $modal.open({
        templateUrl: 'scripts/modules/securityGroups/configure/aws/editSecurityGroup.html',
        controller: 'EditSecurityGroupCtrl as ctrl',
        resolve: {
          securityGroup: function() {
            return angular.copy($scope.securityGroup.plain());
          },
          application: function() { return application; }
        }
      });
    };


    this.cloneSecurityGroup = function cloneSecurityGroup() {
      $modal.open({
        templateUrl: 'scripts/modules/securityGroups/clone/aws/cloneSecurityGroup.html',
        controller: 'CloneSecurityGroupController as ctrl',
        resolve: {
          securityGroup: function() {
            var securityGroup = angular.copy($scope.securityGroup.plain());
            if(securityGroup.region) {
              securityGroup.regions = [securityGroup.region];
            }
            return securityGroup;
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
        provider: 'aws',
        account: securityGroup.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);
