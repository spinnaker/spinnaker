'use strict';


let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.aws.details.controller', [
  require('angular-ui-router'),
  require('angular-bootstrap'),
  require('../../securityGroup.read.service.js'),
  require('../../securityGroup.write.service.js'),
  require('../../../confirmationModal/confirmationModal.service.js'),
  require('../../../utils/lodash.js'),
])
  .controller('awsSecurityGroupDetailsCtrl', function ($scope, $state, securityGroup, application,
                                                    confirmationModalService, securityGroupWriter, securityGroupReader,
                                                    $modal, _) {

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
        template: require('../../configure/aws/editSecurityGroup.html'),
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
        provider: 'aws',
        account: securityGroup.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);
