'use strict';

angular.module('deckApp')
  .factory('oortService', function ($http) {

    function listApplications() {
      return {
        data: [{
          'name' : 'oort',
          'description' : 'Cassandra cluster for cass_gpsemp_euwest1',
          'email' : 'cde_admin@netflix.com',
          'owner' : 'Cloud Database Engineering',
          'type' : 'Standalone Application',
          'group' : null,
          'monitorBucketType' : 'application',
          'pdApiKey' : '626560c0b433012e3b1312313d009e57',
          'regions' : null,
          'tags' : null,
          'createTs' : '1367273703256',
          'updateTs' : '1367273703256'
        }, {
          'name' : 'kato',
          'description' : 'Main ZooKeeper Server',
          'email' : 'dlplatformteam@netflix.com',
          'owner' : 'Platform Team',
          'type' : 'Standalone Application',
          'group' : '',
          'monitorBucketType' : 'application',
          'pdApiKey' : '9aab37d0a3d3012f277422000afc49b7',
          'regions' : null,
          'tags' : null,
          'createTs' : '1314146078914',
          'updateTs' : '1385575519526'
        }, {
          'name' : 'apiproxy',
          'description' : 'Cassandra cluster for cass_seg_skeeball',
          'email' : 'cde_admin@netflix.com',
          'owner' : 'CDE SEG',
          'type' : 'Standalone Application',
          'group' : '',
          'monitorBucketType' : 'application',
          'pdApiKey' : '626560c0b433012e3b1312313d009e57',
          'regions' : null,
          'tags' : 'cde,cassandra',
          'createTs' : '1399576071499',
          'updateTs' : '1399576071499'
        }]
      };
//      return $http.get('http://oort.prod.netflix.net/applications');
    }

    function getApplication(application) {
      return $http.get('http://oort.prod.netflix.net/applications/' + application);
    }

    function getClusters(application) {
      return $http.get('http://oort.prod.netflix.net/applications/' + application + '/clusters');
    }

    function getClustersForAccount(application, account) {
      return $http.get('http://oort.prod.netflix.net/applications/' + application + '/clusters/' + account);
    }

    function getCluster(application, account, cluster) {
      return $http.get('http://oort.prod.netflix.net/applications/' + application + '/clusters/' + account + '/' + cluster);
    }

    function getServerGroup(application, account, cluster, serverGroupName) {
      return $http.get('http://oort.prod.netflix.net/applications/' + application + '/clusters/' + account + '/' + cluster + '/aws/serverGroups/' + serverGroupName);
    }

    return {
      listApplications: listApplications,
      getApplication: getApplication,
      getClusters: getClusters,
      getClustersForAccount: getClustersForAccount,
      getCluster: getCluster,
      getServerGroup: getServerGroup
    };
  });
