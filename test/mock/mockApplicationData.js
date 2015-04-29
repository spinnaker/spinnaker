angular
  .module('cluster.test.data', [])
  .value('applicationJSON', {
      clusters: [
        { name: 'in-us-west-1-only', account: 'test', region: 'us-west-1'},
        { name: 'in-eu-east-2-only', account: 'prod', region: 'eu-east-2'},
      ],
      serverGroups: [
        {cluster: 'in-us-west-1-only', 'account': 'test', region: 'us-west-1', instances: [],
          totalCount: 1, upCount: 0, downCount: 1, unknownCount: 0, startingCount: 0, outOfServiceCount: 0,
          isDisabled: false, type: 'aws', instanceType: 'm3.large'},
        {cluster: 'in-eu-east-2-only', 'account': 'prod', region: 'eu-east-2', instances: [],
          totalCount: 0, upCount: 0, downCount: 0, unknownCount: 0, startingCount: 0, outOfServiceCount: 0,
          isDisabled: true, type:'gce', instanceType: 'm3.medium'}
      ]
    }
  )
  .value('groupedJSON',
    [ {
      heading : 'test',
      subgroups : [ {
        heading : 'in-us-west-1-only',
        subgroups : [ {
          heading : 'us-west-1',
          serverGroups : [ {
            cluster: 'in-us-west-1-only',
            account : 'test',
            region : 'us-west-1',
            instances : [  ],
            totalCount: 1,
            upCount: 0,
            downCount: 1,
            unknownCount: 0,
            startingCount: 0,
            outOfServiceCount: 0,
            isDisabled: false,
            type: 'aws',
            instanceType: 'm3.large'
          } ]
        } ]
      } ] }, {
      heading : 'prod',
      subgroups : [ {
        heading : 'in-eu-east-2-only',
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
            instanceType: 'm3.medium'
          } ]
        } ]
      } ] } ]
  );
