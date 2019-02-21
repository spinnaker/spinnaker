'use strict';

const angular = require('angular');

import { InstanceReader, INSTANCE_WRITE_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.oracle.instance.details.controller', [
    require('@uirouter/angularjs').default,
    INSTANCE_WRITE_SERVICE,
  ])
  .controller('oracleInstanceDetailsCtrl', [
    '$scope',
    '$q',
    'instanceWriter',
    'app',
    'instance',
    function($scope, $q, instanceWriter, app, instance) {
      $scope.application = app;

      let initialize = app.isStandalone
        ? retrieveInstance()
        : $q.all([app.serverGroups.ready()]).then(retrieveInstance);

      initialize.then(() => {
        if (!$scope.$$destroyed && !app.isStandalone) {
          app.serverGroups.onRefresh($scope, retrieveInstance);
        }
      });

      function retrieveInstance() {
        let instanceSummary, account, region;
        if (!$scope.application.serverGroups) {
          instanceSummary = {};
          account = instance.account;
          region = instance.region;
        } else {
          $scope.application.serverGroups.data.some(serverGroup => {
            return serverGroup.instances.some(possibleInstance => {
              if (possibleInstance.id === instance.instanceId || possibleInstance.name === instance.instanceId) {
                instanceSummary = possibleInstance;
                account = serverGroup.account;
                region = serverGroup.region;
                return true;
              }
            });
          });
        }

        $scope.instance = instanceSummary;
        InstanceReader.getInstanceDetails(account, region, instance.instanceId).then(instanceDetails => {
          Object.assign($scope.instance, instanceDetails);
        });
      }
    },
  ]);
