'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.diff.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../utils/lodash.js'),
])
  .factory('diffService', function(_, Restangular) {

    function getClusterDiffForAccount(accountName, clusterName) {
        return Restangular
            .all('diff')
            .all('cluster')
            .one(accountName, clusterName)
            .get().then((diff) => {
                return diff.plain();
            });
    }

    function diffSecurityGroups(securityGroups, clusterDiff, source) {
        if (!clusterDiff) {
            return [];
        }
        return _(clusterDiff.attributeGroups)
            .map((attributeGroup) => {
                return {
                    commonSecurityGroups: attributeGroup.commonAttributes.securityGroups,
                    serverGroups: attributeGroup.identifiers
                };
            })
            .filter((attributeGroup) => {
                return attributeGroup.commonSecurityGroups && !_.isEqual(attributeGroup.commonSecurityGroups, securityGroups);
            })
            .map((attributeGroup) => {
                return {
                    commonSecurityGroups: attributeGroup.commonSecurityGroups,
                    serverGroups: _(attributeGroup.serverGroups)
                        .pluck('location')
                        .merge(_(attributeGroup.serverGroups).pluck('identity').value())
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
  })
  .name;
