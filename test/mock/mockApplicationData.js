angular
  .module('cluster.test.data', [])
  .value('applicationJSON', {
      clusters: [
        {
          serverGroups: [
            {'account': 'test', region: 'us-west-1', instances: [], downCount: 1, "isDisabled": false, type: 'aws', instanceType: 'm3.large'},
            {'account': 'prod', region: 'eu-east-2', instances: [], downCount: 0, "isDisabled": true, type:'gce', instanceType: 'm3.medium'}
          ]
        }
      ]
    }
  )
  .value('groupedJSON',
    [ {
      heading : 'test',
      subgroups : [ {
        heading : 'us-west-1',
        subgroups : [ {
          heading : 'undefined',
          serverGroups : [ {
            account : 'test',
            region : 'us-west-1',
            instances : [  ],
            downCount: 1,
            isDisabled: false,
            type: 'aws',
            instanceType: 'm3.large'
          } ]
        } ]
      } ] }, {
      heading : 'prod',
      subgroups : [ {
        heading : 'eu-east-2',
        subgroups : [ {
          heading : 'undefined',
          serverGroups : [ {
            account : 'prod',
            region : 'eu-east-2',
            instances : [  ],
            downCount: 0,
            isDisabled: true,
            type: 'gce',
            instanceType: 'm3.medium'
          } ]
        } ]
      } ] } ]
  );
