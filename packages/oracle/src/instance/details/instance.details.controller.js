'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';

import { InstanceReader } from '@spinnaker/core';

export const ORACLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER = 'spinnaker.oracle.instance.details.controller';
export const name = ORACLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER; // for backwards compatibility
module(ORACLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER, [UIROUTER_ANGULARJS]).controller(
  'oracleInstanceDetailsCtrl',
  [
    '$scope',
    '$q',
    'app',
    'instance',
    function ($scope, $q, app, instance) {
      $scope.application = app;

      const initialize = app.isStandalone
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
          $scope.application.serverGroups.data.some((serverGroup) => {
            return serverGroup.instances.some((possibleInstance) => {
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
        InstanceReader.getInstanceDetails(account, region, instance.instanceId).then((instanceDetails) => {
          Object.assign($scope.instance, instanceDetails);
        });
      }
    },
  ],
);
