'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastPropertiesScope.service', [
    require('../../core/naming/naming.service.js'),
    require('../../core/utils/lodash.js'),
  ])
  .factory('FastPropertyScopeService', function ($q, namingService, _) {

    function regionTransformer(appId, clusters) {
      return _.chain(clusters)
        .pluck('serverGroups')
        .map(function(serverGroupList) {
          return _.map(serverGroupList, function(serverGroup){
            return serverGroup.region;
          });
        })
        .flatten()
        .unique()
        .map(function(regionName) {
          return {
            scope: {
              appId: appId,
              region: regionName,
            },
            primary: regionName,
            secondary: []
          };
        })
        .value();
    }

    function stackTransformer(appId, clusters) {
      return _.chain(clusters)
        .pluck('serverGroups')
        .map(function (serverGroupList) {
          return _.map(serverGroupList, function (serverGroup) {
            var stack = namingService.parseServerGroupName(serverGroup.name).stack;
            return {
              scope: {
                appId: appId,
                region: serverGroup.region,
                stack: stack,
              },
              primary: stack,
              secondary:[ serverGroup.region ]
            };
          });
        })
        .flatten()
        .unique()
        .value();
    }

    function clusterTransformer(appId, clusters) {
      return _.chain(clusters)
        .pluck('name')
        .unique()
        .map(function(clusterName) {
          return {
            scope: {
              appId: appId,
              cluster: clusterName
            },
            primary: clusterName,
            secondary: []
          };
        })
        .value();
    }


    function asgTransformer(appId, clusters) {
      return _.chain(clusters)
        .map(function(cluster) {
          return _(cluster.serverGroups)
            .chain()
            .flatten()
            .map(function(serverGroup){
              serverGroup.cluster = cluster.name;
              return serverGroup;
            })
            .map(function(serverGroup) {
              return {
                scope: {
                  appId: appId,
                  region: serverGroup.region,
                  cluster: serverGroup.cluster,
                  asg: serverGroup.name
                },
                primary: serverGroup.name,
                secondary: [serverGroup.cluster, serverGroup.region]
              };
            })
            .value();
        })
        .flatten()
        .value();
    }

    function zoneTransformer(appId, clusters) {
      return _(clusters)
        .chain()
        .map(function(cluster) {
          return _(cluster.serverGroups)
            .chain()
            .flatten()
            .map(function(serverGroup){
              return _(serverGroup.instances)
                .chain()
                .map(function (instance) {
                  instance.cluster = cluster.name;
                  instance.region = serverGroup.region;
                  instance.asg = serverGroup.name;
                  return instance;
                })
                .flatten()
                .value();
            })
            .flatten()
            .value();
        })
        .flatten()
        .map(function(instance) {
          return {
            scope: {
              appId: appId,
              region: instance.region,
              cluster: instance.cluster,
              asg: instance.asg,
              zone: instance.availabilityZone
            },
            primary: instance.availabilityZone,
            secondary: [instance.asg, instance.cluster, instance.region]
          };
        })
        .value();
    }

    function instanceTransformer(appId, clusters) {
      return _(clusters)
        .chain()
        .map(function(cluster) {
          return _(cluster.serverGroups)
            .chain()
            .flatten()
            .map(function(serverGroup) {
              return _(serverGroup.instances)
                .chain()
                .map(function (instance) {
                  instance.cluster = cluster.name;
                  instance.region = serverGroup.region;
                  instance.asg = serverGroup.name;
                  return instance;
                })
                .flatten()
                .value();
            })
            .flatten()
            .value();
        })
        .flatten()
        .map(function(instance) {
          return {
            scope: {
              appId: appId,
              region: instance.region,
              cluster: instance.cluster,
              asg: instance.asg,
              zone: instance.availabilityZone,
              serverId: instance.id,
            },
            primary: instance.id,
            secondary: [instance.availabilityZone, instance.asg, instance.cluster, instance.region]
          };
        })
        .value();
    }

    function appIdTransformer(appId) {
      return [{
        scope: {
          appId: appId
        },
        primary: appId,
        secondary: [],
      }];
    }

    var transformers = {
      'appId': appIdTransformer,
      'region': regionTransformer,
      'stack': stackTransformer,
      'cluster': clusterTransformer,
      'asg' : asgTransformer,
      'zone' : zoneTransformer,
      'serverId' : instanceTransformer,
    };

    var extractScopeFromHistoryMessage = (messageString) => {
      let regex = /(?:Scope\()(.+?)\)/;
      let prefexRegex = /.+?(?=Selection)/;
      let prefixResult = prefexRegex.exec(messageString);
      let resultArray = regex.exec(messageString) || [];
      return prefixResult && resultArray.length > 1 ? `${prefixResult}: ${resultArray[1].split(',').join(', ')}` : messageString;
    };

    function getResultsForScope(appId, clusterList, scope) {
      var deferred = $q.defer();
      deferred.resolve(transformers[scope](appId, clusterList));
      return deferred.promise;
    }

    return {
      getResultsForScope: getResultsForScope,
      extractScopeFromHistoryMessage: extractScopeFromHistoryMessage
    };

  });
