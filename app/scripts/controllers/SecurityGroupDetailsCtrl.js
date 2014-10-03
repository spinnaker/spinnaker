'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('SecurityGroupDetailsCtrl', function ($scope, $rootScope, securityGroup, application, securityGroupService, $modal) {

    $scope.loading = true;

    function extractSecurityGroup() {
      securityGroupService.getSecurityGroupDetails(application, securityGroup.accountId, securityGroup.region, securityGroup.name).then(function (details) {
        $scope.loading = false;
        $scope.securityGroup = details;
      });
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
