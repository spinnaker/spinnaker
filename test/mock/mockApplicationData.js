let angular = require('angular');

module.exports = angular
  .module('cluster.test.data', [])
  .value('applicationJSON', {
      clusters: [
        { name: 'in-eu-east-2-only', account: 'prod', region: 'eu-east-2', category: 'serverGroup' },
        { name: 'in-us-west-1-only', account: 'test', region: 'us-west-1', category: 'serverGroup' },
      ],
      serverGroups: { data: [
        {cluster: 'in-eu-east-2-only', 'account': 'prod', region: 'eu-east-2', instances: [], name: 'in-eu-east-2-only',
          instanceCounts: {total: 0, up: 0, down: 0, unknown: 0, starting: 0, outOfService: 0 },
          isDisabled: true, type:'gce', instanceType: 'm3.medium', vpcName: '', category: 'serverGroup' },
        {cluster: 'in-us-west-1-only', 'account': 'test', region: 'us-west-1', instances: [ {} ], name: 'in-us-west-1-only',
          instanceCounts: {total: 1, up: 0, down: 1, unknown: 0, starting: 0, outOfService: 0},
          isDisabled: false, type: 'aws', instanceType: 'm3.large', vpcName: 'Main', category: 'serverGroup' },
      ]}
    }
  )
  .constant('groupedJSON',
    [ {
      heading : 'prod',
      subgroups : [ {
        heading : 'in-eu-east-2-only',
        category: 'serverGroup',
        hasDiscovery: false,
        hasLoadBalancers: false,
        subgroups : [ {
          heading : 'eu-east-2',
          category: 'serverGroup',
          serverGroups : [ {
            cluster: 'in-eu-east-2-only',
            category: 'serverGroup',
            account : 'prod',
            region : 'eu-east-2',
            instances : [ ],
            name: 'in-eu-east-2-only',
            instanceCounts: {
              total: 0,
              up: 0,
              down: 0,
              unknown: 0,
              starting: 0,
              outOfService: 0,
            },
            isDisabled: true,
            type: 'gce',
            instanceType: 'm3.medium',
            vpcName: '',
          } ]
        } ]
      } ] },
      {
        heading : 'test',
        subgroups : [ {
          heading : 'in-us-west-1-only',
          category: 'serverGroup',
          hasDiscovery: false,
          hasLoadBalancers: false,
          subgroups : [ {
            heading : 'us-west-1',
            category: 'serverGroup',
            serverGroups : [ {
              cluster: 'in-us-west-1-only',
              category: 'serverGroup',
              account : 'test',
              region : 'us-west-1',
              instances : [ {} ],
              name: 'in-us-west-1-only',
              instanceCounts: {
                total: 1,
                up: 0,
                down: 1,
                unknown: 0,
                starting: 0,
                outOfService: 0,
              },
              isDisabled: false,
              type: 'aws',
              instanceType: 'm3.large',
              vpcName: 'Main',
            } ]
          } ]
        } ] } ]
  );
