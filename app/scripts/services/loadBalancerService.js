'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('loadBalancerService', function (searchService, settings, $q, Restangular, _) {

    var oortEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.oortUrl);
    });

    function loadLoadBalancers(application) {
      var loadBalancerNames = [],
        loadBalancerPromises = [];

      application.accounts.forEach(function(account) {
        var accountClusters = application.clusters[account];
        accountClusters.forEach(function(cluster) {
          loadBalancerNames.push(cluster.loadBalancers);
        });
      });

      loadBalancerNames = _.unique(_.flatten(loadBalancerNames));

      loadBalancerNames.forEach(function(loadBalancer) {
        var loadBalancerPromise = getLoadBalancer(loadBalancer, application);
        loadBalancerPromises.push(loadBalancerPromise);
      });

      return $q.all(loadBalancerPromises).then(_.flatten);

    }

    function updateHealthCounts(loadBalancer) {
      var instances = loadBalancer.instances;
      loadBalancer.healthCounts = {
        upCount: instances.filter(function (instance) {
          return instance.healthStatus === 'Healthy';
        }).length,
        downCount: instances.filter(function (instance) {
          return instance.healthStatus === 'Unhealthy';
        }).length,
        unknownCount: instances.filter(function (instance) {
          return instance.healthStatus === 'Unknown';
        }).length
      };
    }

    function getLoadBalancer(name) {
      var promise = oortEndpoint.one('aws').one('loadBalancers', name).get();
      return promise.then(function(loadBalancerRollup) {
        if (angular.isUndefined(loadBalancerRollup)) { return []; }
        var loadBalancers = [];
        loadBalancerRollup.accounts.forEach(function (account) {
          account.regions.forEach(function (region) {
            region.loadBalancers.forEach(function (loadBalancer) {
              loadBalancer.account = account.name;
              loadBalancers.push(loadBalancer);
            });
          });
        });
        return loadBalancers;
      });
    }

    function normalizeLoadBalancersWithServerGroups(application) {
      application.loadBalancers.forEach(function(loadBalancer) {
        var serverGroups = application.serverGroups.filter(function(serverGroup) {
          return serverGroupIsInLoadBalancer(serverGroup, loadBalancer);
        });
        loadBalancer.serverGroups = serverGroups;
        loadBalancer.instances =  _.flatten(_.collect(serverGroups, 'instances'));
        updateHealthCounts(loadBalancer);
      });
    }

    function serverGroupIsInLoadBalancer(serverGroup, loadBalancer) {
      if (serverGroup.region !== loadBalancer.region || loadBalancer.serverGroups.indexOf(serverGroup.name) === -1) {
        return false;
      }
      // only include if load balancer is fronting an instance
      try {
        var elbInstanceIds = _.pluck(loadBalancer.elb.instances, 'instanceId'),
          serverGroupInstanceIds = _.pluck(serverGroup.instances, 'instanceId');
        return elbInstanceIds.some(function (elbInstanceId) {
          return serverGroupInstanceIds.indexOf(elbInstanceId) !== -1;
        });
      } catch (e) {
        return false;
      }
    }

    function convertLoadBalancerForEditing(loadBalancer) {
      var toEdit = {
        editMode: true,
        region: loadBalancer.region,
        credentials: loadBalancer.account,
        listeners: [],
        name: loadBalancer.name,
        regionZones: loadBalancer.availabilityZones
      };

      if (loadBalancer.elb) {
        var elb = loadBalancer.elb;

        toEdit.securityGroups = elb.securityGroups;
        toEdit.vpcId = elb.vpcid;

        if (elb.listenerDescriptions) {
          toEdit.listeners = elb.listenerDescriptions.map(function (description) {
            var listener = description.listener;
            return {
              internalProtocol: listener.instanceProtocol,
              internalPort: listener.instancePort,
              externalProtocol: listener.protocol,
              externalPort: listener.loadBalancerPort
            };
          });
        }

        if (elb.healthCheck && elb.healthCheck.target) {
          var healthCheck = loadBalancer.elb.healthCheck.target;
          var protocolIndex = healthCheck.indexOf(':'),
            pathIndex = healthCheck.indexOf('/');

          if (protocolIndex !== -1 && pathIndex !== -1) {
            toEdit.healthCheckProtocol = healthCheck.substring(0, protocolIndex);
            toEdit.healthCheckPort = healthCheck.substring(protocolIndex + 1, pathIndex);
            toEdit.healthCheckPath = healthCheck.substring(pathIndex);
            if (!isNaN(toEdit.healthCheckPort)) {
              toEdit.healthCheckPort = Number(toEdit.healthCheckPort);
            }
          }
        }
      }
      return toEdit;
    }

    function constructNewLoadBalancerTemplate() {
      return {
        credentials: null,
        vpcId: 'none',
        healthCheckProtocol: 'HTTPS',
        healthCheckPort: 7001,
        healthCheckPath: '/health',
        regionZones: [],
        securityGroups: [],
        listeners: [
          {
            internalProtocol: 'HTTP',
            internalPort: 7001,
            externalProtocol: 'HTTP',
            externalPort: 80
          }
        ]
      };
    }

    return {
      loadLoadBalancers: loadLoadBalancers,
      normalizeLoadBalancersWithServerGroups: normalizeLoadBalancersWithServerGroups,
      serverGroupIsInLoadBalancer: serverGroupIsInLoadBalancer,
      convertLoadBalancerForEditing: convertLoadBalancerForEditing,
      constructNewLoadBalancerTemplate: constructNewLoadBalancerTemplate
    };

  });
