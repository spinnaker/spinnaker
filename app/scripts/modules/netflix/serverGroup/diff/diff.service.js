'use strict';

import _ from 'lodash';
import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.diff.service', [
  API_SERVICE,
  require('core/config/settings.js'),
])
  .factory('diffService', function (API, $q, settings) {

    // TODO: Consider removing entirely after 11/08/16 if nobody asks about the feature being turned off
    function getClusterDiffForAccount(accountName, clusterName) {
      if (!settings.feature.clusterDiff) {
        return $q.when({});
      }
      return API
        .all('diff')
        .all('cluster')
        .one(accountName, clusterName)
        .get().then(
          (diff) => {
            return diff;
          },
          () => {
            return {};
          }
      );
    }

    function diffSecurityGroups(securityGroups, clusterDiff, source) {
      if (!clusterDiff) {
        return [];
      }
      return _.chain(clusterDiff.attributeGroups)
        .map((attributeGroup) => {
          return {
            commonSecurityGroups: attributeGroup.commonAttributes.securityGroups,
            serverGroups: attributeGroup.identifiers
          };
        })
        .filter((attributeGroup) => {
          return attributeGroup.commonSecurityGroups && !_.isEqual(attributeGroup.commonSecurityGroups.sort(),
              securityGroups.sort());
        })
        .map((attributeGroup) => {
          return {
            commonSecurityGroups: attributeGroup.commonSecurityGroups,
            serverGroups: _.chain(attributeGroup.serverGroups)
              .map('location')
              .merge(_.chain(attributeGroup.serverGroups).map('identity').value())
              .filter((serverGroup) => {
                if (source) {
                  var serverGroupIdentity = {
                    account: serverGroup.account,
                    region: serverGroup.region,
                    asgName: serverGroup.autoScalingGroupName,
                  };
                  return !_.isEqual(serverGroupIdentity, source);
                }
                return true;
              })
              .sortBy('account', 'region', 'autoScalingGroupName')
              .value()
          };
        })
        .filter((attributeGroup) => {
          return attributeGroup.serverGroups.length;
        })
        .value();
    }

    return {
      getClusterDiffForAccount: getClusterDiffForAccount,
      diffSecurityGroups: diffSecurityGroups,
    };
  });
