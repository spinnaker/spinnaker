let angular = require('angular');

module.exports = angular
  .module('cluster.test.data', [])
  .value('applicationJSON', {
      clusters: [
        { name: 'in-eu-east-2-only', account: 'prod', region: 'eu-east-2'},
        { name: 'in-us-west-1-only', account: 'test', region: 'us-west-1'},
      ],
      serverGroups: [
        {cluster: 'in-eu-east-2-only', 'account': 'prod', region: 'eu-east-2', instances: [],
          totalCount: 0, upCount: 0, downCount: 0, unknownCount: 0, startingCount: 0, outOfServiceCount: 0,
          isDisabled: true, type:'gce', instanceType: 'm3.medium', vpcName: ''},
        {cluster: 'in-us-west-1-only', 'account': 'test', region: 'us-west-1', instances: [ {} ],
          totalCount: 1, upCount: 0, downCount: 1, unknownCount: 0, startingCount: 0, outOfServiceCount: 0,
          isDisabled: false, type: 'aws', instanceType: 'm3.large', vpcName: 'Main',},
      ]
    }
  )
  .constant('groupedJSON',
    [ {
      heading : 'prod',
      subgroups : [ {
        heading : 'in-eu-east-2-only',
        hasDiscovery: false,
        hasLoadBalancers: false,
        subgroups : [ {
          heading : 'eu-east-2',
          serverGroups : [ {
            cluster: 'in-eu-east-2-only',
            account : 'prod',
            region : 'eu-east-2',
            instances : [  ],
            totalCount: 0,
            upCount: 0,
            downCount: 0,
            unknownCount: 0,
            startingCount: 0,
            outOfServiceCount: 0,
            isDisabled: true,
            type: 'gce',
            instanceType: 'm3.medium',
            vpcName: ''
          } ]
        } ]
      } ] },
      {
        heading : 'test',
        subgroups : [ {
          heading : 'in-us-west-1-only',
          hasDiscovery: false,
          hasLoadBalancers: false,
          subgroups : [ {
            heading : 'us-west-1',
            serverGroups : [ {
              cluster: 'in-us-west-1-only',
              account : 'test',
              region : 'us-west-1',
              instances : [ {} ],
              totalCount: 1,
              upCount: 0,
              downCount: 1,
              unknownCount: 0,
              startingCount: 0,
              outOfServiceCount: 0,
              isDisabled: false,
              type: 'aws',
              instanceType: 'm3.large',
              vpcName: 'Main',
            } ]
          } ]
        } ] } ]
  )
  .name;
