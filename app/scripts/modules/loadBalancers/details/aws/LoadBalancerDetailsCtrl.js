'use strict';


angular.module('deckApp.loadBalancer.aws.details.controller',[
  'ui.router',
  'ui.bootstrap',
  'deckApp.notifications.service',
  'deckApp.securityGroup.read.service',
  'deckApp.loadBalancer.write.service',
  'deckApp.loadBalancer.read.service',
  'deckApp.utils.lodash',
  'deckApp.confirmationModal.service'
])
  .controller('awsLoadBalancerDetailsCtrl', function ($scope, $state, $exceptionHandler, $modal, notificationsService, loadBalancer, application,
                                                   securityGroupReader, _, confirmationModalService, loadBalancerWriter, loadBalancerReader) {

    $scope.state = {
      loading: true
    };

    function extractLoadBalancer() {
      if (!loadBalancer.vpcId) {
        loadBalancer.vpcId = null;
      }
      $scope.loadBalancer = application.loadBalancers.filter(function (test) {
        var testVpc = test.vpcId || null;
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.accountId && testVpc === loadBalancer.vpcId;
      })[0];

      if ($scope.loadBalancer) {
        var detailsLoader = loadBalancerReader.getLoadBalancerDetails($scope.loadBalancer.provider, loadBalancer.accountId, loadBalancer.region, loadBalancer.name);
        detailsLoader.then(function(details) {
          $scope.state.loading = false;
          var securityGroups = [];
          var filtered = details.filter(function(test) {
            return test.vpcid === loadBalancer.vpcId || (!test.vpcid && !loadBalancer.vpcId);
          });
          if (filtered.length) {
            $scope.loadBalancer.elb = filtered[0];
            $scope.loadBalancer.account = loadBalancer.accountId;

            if ($scope.loadBalancer.elb.availabilityZones) {
              $scope.loadBalancer.elb.availabilityZones.sort();
            }

            $scope.loadBalancer.elb.securityGroups.forEach(function (securityGroupId) {
              var match = securityGroupReader.getApplicationSecurityGroup(application, loadBalancer.accountId, loadBalancer.region, securityGroupId);
              if (match) {
                securityGroups.push(match);
              }
            });
            $scope.securityGroups = _.sortBy(securityGroups, 'name');
          }
        });
      }
      if (!$scope.loadBalancer) {
        notificationsService.create({
          message: 'No load balancer named "' + loadBalancer.name + '" was found in ' + loadBalancer.accountId + ':' + loadBalancer.region,
          autoDismiss: true,
          hideTimestamp: true,
          strong: true
        });
        $state.go('^');
      }
    }

    extractLoadBalancer();

    application.registerAutoRefreshHandler(extractLoadBalancer, $scope);

    this.editLoadBalancer = function editLoadBalancer() {
      var provider = $scope.loadBalancer.provider;
      $modal.open({
        templateUrl: 'scripts/modules/loadBalancers/configure/' + provider + '/editLoadBalancer.html',
        controller: provider + 'CreateLoadBalancerCtrl as ctrl',
        resolve: {
          application: function() { return application; },
          loadBalancer: function() { return angular.copy($scope.loadBalancer); },
          isNew: function() { return false; }
        }
      });
    };

    this.deleteLoadBalancer = function deleteLoadBalancer() {
      if ($scope.loadBalancer.instances && $scope.loadBalancer.instances.length) {
        return;
      }

      var taskMonitor = {
        application: application,
        title: 'Deleting ' + loadBalancer.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true
      };

      var submitMethod = function () {
        loadBalancer.providerType = $scope.loadBalancer.type;
        loadBalancer.vpcId = $scope.loadBalancer.elb.vpcid || null;
        return loadBalancerWriter.deleteLoadBalancer(loadBalancer, application);
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + loadBalancer.name + '?',
        buttonText: 'Delete ' + loadBalancer.name,
        destructive: true,
        provider: 'aws',
        account: loadBalancer.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);
