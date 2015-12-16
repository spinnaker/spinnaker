'use strict';

describe('FastPropertyScopeService', function () {
  var service;
  var $q;
  var $rootScope;
  var appId = 'mahe';


  beforeEach(
    window.module(
      require('./fastPropertyScope.service')
    )
  );

  beforeEach(window.inject(function (_$q_, _$rootScope_, _FastPropertyScopeService_) {
    $rootScope = _$rootScope_;
    $q = _$q_;
    service = _FastPropertyScopeService_;
  }));

  describe('when there are no server groups', function () {
    var clusters = [];
    ['region', 'stack', 'cluster', 'asg', 'zone', 'serverId'].forEach(function (scope) {

      it('should return and empty list: ' + scope, function () {
        var resultList;
        service.getResultsForScope(appId, clusters, scope).then(function(result){resultList = result; });

        $rootScope.$apply();

        expect(resultList.length).toBe(0);
        expect(resultList).toEqual([]);
      });

    });
  });



  describe('REGION SCOPE: ', function () {
    describe('when all instances are in the same region', function () {

       var clusters = [
         createCluster('prod', 'mahe-main')
           .addServerGroups('us-west-1', 'mahe-main-v000')
           .addInstance('i-8d678d46', 'us-west-1a'),

         createCluster('prod', 'mahe-prestaging')
           .addServerGroups('us-west-1')
           .addInstance('i-9d6l8d40', 'us-west-1c')
       ];

      it('should return one result for region', function () {

        var resultList;
        service.getResultsForScope(appId, clusters, 'region').then(function(result) {resultList = result; });
        $rootScope.$apply();

        expect(resultList.length).toBe(1);
        expect(resultList).toEqual([{
          scope:{
            appId:'mahe',
            region:'us-west-1'
          },
          primary:'us-west-1',
          secondary: []
        }]);
      });

    });

    describe('when all serverGroups are in the different regions', function () {

      var clusters = [
        createCluster('prod', 'mahe-main')
          .addServerGroups('us-west-1', 'mahe-main-v000')
          .addInstance('i-8d678d46', 'us-west-1a'),

        createCluster('prod', 'mahe-prestaging')
          .addServerGroups('us-west-2', 'mahe-prestaging-v000')
          .addInstance('i-9d6l8d40', 'us-west-1c')
      ];

      it('should return 2 results for the 2 unique regions', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'region').then(function(result) {resultList = result; });
        $rootScope.$apply();

        expect(resultList.length).toBe(2);
        expect(resultList).toEqual([
          {
            scope:{
              appId: appId,
              region:'us-west-1'
            },
            primary: 'us-west-1',
            secondary:[]

          },
          {
            scope: {
              appId: appId,
              region:'us-west-2'
            },
            primary: 'us-west-2',
            secondary: []
          }]);
      });

    });

  });

  describe('STACK SCOPE: ', function () {
    describe('when there is only one serverGroup with one stack ', function () {
      var clusters = [
        createCluster('prod', 'mahe-main')
          .addServerGroups('us-west-1', 'mahe-main-v001')
      ];

      it('should return a list of 1 for the stack', function () {

        var resultList;
        service.getResultsForScope(appId, clusters, 'stack').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(1);
        expect(resultList).toEqual([
          {
            scope: {
              appId: appId,
              region: 'us-west-1',
              stack: 'main'
            },
            primary: 'main',
            secondary: ['us-west-1']

          }
        ]);
      });
    });

    describe('when there are 2 serverGroups with different stack names in the same region', function () {
      var clusters = [
        createCluster('prod', 'mahe-main')
          .addServerGroups('us-west-1', 'mahe-main-v000'),
        createCluster('prod', 'mahe-prestaging')
          .addServerGroups('us-west-1', 'mahe-prestaging-v000')
      ];

      it('should return a list of 2 scopes, 1 for each stack', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'stack').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(2);
        expect(resultList).toEqual([
          {
            scope: {
              appId: appId,
              region: 'us-west-1',
              stack: 'main'
            },
            primary: 'main',
            secondary: ['us-west-1']
          },
          {
            scope: {
              appId: appId,
              region: 'us-west-1',
              stack: 'prestaging'
            },
            primary: 'prestaging',
            secondary: ['us-west-1']
          },
        ]);

      });
    });

    describe('when there are 2 serverGroups with different stack names in the different regions', function () {
      var clusters = [
        createCluster('prod', 'mahe-main')
          .addServerGroups('us-west-1', 'mahe-main-v000'),
        createCluster('prod', 'mahe-prestaging')
          .addServerGroups('us-west-2', 'mahe-prestaging-v000')
      ];

      it('should return a list of 2 scopes, 1 for each stack', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'stack').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(2);
        expect(resultList).toEqual([
          {
            scope: {
              appId: appId,
              region: 'us-west-1',
              stack: 'main'
            },
            primary: 'main',
            secondary: ['us-west-1']
          },
          {
            scope: {
              appId: appId,
              region: 'us-west-2',
              stack: 'prestaging'
            },
            primary: 'prestaging',
            secondary: ['us-west-2']
          },
        ]);

      });
    });

    describe('when there are 2 serverGroups with same stack names in the different regions', function () {
      var clusters = [
        createCluster('prod', 'mahe-main')
          .addServerGroups('us-west-1', 'mahe-main-v000'),
        createCluster('prod', 'mahe-prestaging')
          .addServerGroups('us-west-2', 'mahe-main-v000')
      ];

      it('should return a list of 2 scopes, 1 for each stack', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'stack').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(2);
        expect(resultList).toEqual([
          {
            scope: {
              appId: appId,
              region: 'us-west-1',
              stack: 'main'
            },
            primary: 'main',
            secondary: ['us-west-1']
          },
          {
            scope: {
              appId: appId,
              region: 'us-west-2',
              stack: 'main'
            },
            primary: 'main',
            secondary: ['us-west-2']
          },
        ]);

      });
    });

  });


  describe('CLUSTER SCOPE', function () {
    describe('when one cluster exists in the app', function () {
      var clusters = [
        createCluster('prod', 'mahe-main')
      ];

      it('should return 1 item for the 1 cluster', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'cluster').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(1);
        expect(resultList).toEqual(
          [
            {
              scope: {
                appId: appId,
                cluster: 'mahe-main'
              },
              primary: 'mahe-main',
              secondary: []
            }
          ]
        );

      });
    });


    describe('when multiple clusters exist in the same app', function () {

      var clusters = [
        createCluster('prod', 'mahe-main'),
        createCluster('prod', 'mahe-prestaging')
      ];

      it('should return 2 items kfor the 2 unique regions', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'cluster').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(2);
        expect(resultList).toEqual([
          {
            scope:{
              appId: appId,
              cluster: 'mahe-main'
            },
            primary: 'mahe-main',
            secondary: []

          },
          {
            scope: {
              appId: appId,
              cluster:'mahe-prestaging'
            },
            primary: 'mahe-prestaging',
            secondary: []
          }]);
      });

    });

  });


  describe('ASG SCOPE:', function () {
    describe('when there is one cluster with one ASG ', function () {
      var clusters = [
        createCluster('prod', 'mahe-main')
          .addServerGroups('us-west-1', 'mahe-main-v000')
      ];

      it('should create 1 ASG scope', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'asg').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(1);
        expect(resultList[0].primary).toBe('mahe-main-v000');
        expect(resultList[0].secondary).toEqual(['mahe-main', 'us-west-1']);
        expect(resultList[0].scope).toEqual({
          appId: appId,
          region: 'us-west-1',
          cluster: 'mahe-main',
          asg: 'mahe-main-v000'
        });
      });
    });

    describe('when there are multiple clusters with one ASG ', function () {
      var clusters = [
        createCluster('prod', 'mahe-main')
          .addServerGroups('us-west-1', 'mahe-main-v000'),
        createCluster('prod', 'mahe-prestaging')
          .addServerGroups('us-west-2', 'mahe-prestaging-v000'),
      ];

      it('should create 2 ASG scope', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'asg').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(2);
        //expect(resultList).toEqual({});
        expect(resultList[0].primary).toBe('mahe-main-v000');
        expect(resultList[0].secondary).toEqual(['mahe-main', 'us-west-1']);
        expect(resultList[0].scope).toEqual({
          appId: appId,
          region: 'us-west-1',
          cluster: 'mahe-main',
          asg: 'mahe-main-v000'
        });

        expect(resultList[1].primary).toBe('mahe-prestaging-v000');
        expect(resultList[1].secondary).toEqual(['mahe-prestaging', 'us-west-2']);
        expect(resultList[1].scope).toEqual({
          appId: appId,
          region: 'us-west-2',
          cluster: 'mahe-prestaging',
          asg: 'mahe-prestaging-v000'
        });
      });
    });

  });

  describe('ZONE SCOPE:', function () {

    describe('when there is a cluster with one instance in an availability zone', function () {

      var clusters = [
        createCluster('prod', 'mahe-main')
          .addServerGroups('us-west-1', 'mahe-main-v000')
          .addInstance('i-9d6l8d40', 'us-west-1c')
      ];

      it('should create list w/ 1 AZ scope ', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'zone').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(1);
        expect(resultList[0].primary).toEqual('us-west-1c');
        expect(resultList[0].secondary).toEqual(['mahe-main-v000', 'mahe-main', 'us-west-1']);
        expect(resultList[0].scope).toEqual({
          appId: appId,
          region: 'us-west-1',
          cluster: 'mahe-main',
          asg:'mahe-main-v000',
          zone: 'us-west-1c'
        });

      });
    });

    describe('when there is multiple cluster with one instance in an availability zone', function () {

      var clusters = [
        createCluster('prod', 'mahe-main')
          .addServerGroups('us-west-1', 'mahe-main-v000')
          .addInstance('i-9d6l8d40', 'us-west-1c'),

        createCluster('prod', 'mahe-prestaging')
          .addServerGroups('us-east-2', 'mahe-prestaging-v000')
          .addInstance('i-2h6l8d40', 'us-east-2c'),

      ];

      it('should create list w/ 2 AZ scope ', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'zone').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(2);
        //expect(resultList).toEqual({});
        expect(resultList[0].primary).toEqual('us-west-1c');
        expect(resultList[0].secondary).toEqual(['mahe-main-v000', 'mahe-main', 'us-west-1']);
        expect(resultList[0].scope).toEqual({
          appId: appId,
          region: 'us-west-1',
          cluster: 'mahe-main',
          asg:'mahe-main-v000',
          zone: 'us-west-1c'
        });

        expect(resultList[1].primary).toEqual('us-east-2c');
        expect(resultList[1].secondary).toEqual(['mahe-prestaging-v000', 'mahe-prestaging', 'us-east-2']);
        expect(resultList[1].scope).toEqual({
          appId: appId,
          region: 'us-east-2',
          cluster: 'mahe-prestaging',
          asg:'mahe-prestaging-v000',
          zone: 'us-east-2c'
        });
      });
    });
  });


  describe('IMAGE SCOPE', function () {
    describe('when there is a cluster with 1 instance', function () {

      var clusters = [
        createCluster('prod', 'mahe-main')
          .addServerGroups('us-west-1', 'mahe-main-v000')
          .addInstance('i-9d6l8d40', 'us-west-1c')
      ];

      it('should create a list with 1 instance scope', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'serverId').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(1);
        expect(resultList[0].primary).toEqual('i-9d6l8d40');
        expect(resultList[0].secondary).toEqual(['us-west-1c', 'mahe-main-v000', 'mahe-main', 'us-west-1']);
        expect(resultList[0].scope).toEqual({
          appId: appId,
          region: 'us-west-1',
          cluster: 'mahe-main',
          asg:'mahe-main-v000',
          zone: 'us-west-1c',
          serverId: 'i-9d6l8d40'
        });

      });
    });

    describe('when there are multiple clusters with 1 instance', function () {

      var clusters = [
        createCluster('prod', 'mahe-main')
          .addServerGroups('us-west-1', 'mahe-main-v000')
          .addInstance('i-9d6l8d40', 'us-west-1c'),

        createCluster('prod', 'mahe-prestaging')
          .addServerGroups('us-east-1', 'mahe-prestaging-v000')
          .addInstance('i-7d8lhd42', 'us-east-1c')
      ];

      it('should create a list with 1 instance scope', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'serverId').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(2);
        expect(resultList[0].primary).toEqual('i-9d6l8d40');
        expect(resultList[0].secondary).toEqual(['us-west-1c', 'mahe-main-v000', 'mahe-main', 'us-west-1']);
        expect(resultList[0].scope).toEqual({
          appId: appId,
          region: 'us-west-1',
          cluster: 'mahe-main',
          asg:'mahe-main-v000',
          zone: 'us-west-1c',
          serverId: 'i-9d6l8d40'
        });

        expect(resultList[1].primary).toEqual('i-7d8lhd42');
        expect(resultList[1].secondary).toEqual(['us-east-1c', 'mahe-prestaging-v000', 'mahe-prestaging', 'us-east-1']);
        expect(resultList[1].scope).toEqual({
          appId: appId,
          region: 'us-east-1',
          cluster: 'mahe-prestaging',
          asg:'mahe-prestaging-v000',
          zone: 'us-east-1c',
          serverId: 'i-7d8lhd42'
        });

      });
    });

  });

  describe('CLUSTER SCOPE', function () {
    describe('when one cluster exists in the app', function () {
      var clusters = [
        createCluster('prod', 'mahe-main')
      ];

      it('should return 1 item for the 1 cluster', function () {
        var resultList;
        service.getResultsForScope(appId, clusters, 'cluster').then(function (result) {
          resultList = result;
        });
        $rootScope.$apply();

        expect(resultList.length).toBe(1);
        expect(resultList).toEqual(
          [
            {
              scope: {
                appId: appId,
                cluster: 'mahe-main'
              },
              primary: 'mahe-main',
              secondary: []
            }
          ]
        );

      });
    });
  });

  describe('extract scope array from history message', function () {
    it('should pull out the scope array if there is a Scope string in the message', function () {
      let message = 'Proceeded to ScopeSelection(Scope(prod,gate,main,us-west-1,gate-main),Some(InstanceSelection(1)))';

      let result = service.extractScopeFromHistoryMessage(message);

      expect(result).toEqual('Proceeded to Scope: prod, gate, main, us-west-1, gate-main');
    });

    it('should return the original message if there is no Scope in the message', function () {
      let message = 'Requested';

      let result = service.extractScopeFromHistoryMessage(message);

      expect(result).toEqual(message);

    });
  });

  function createCluster(account, name) {

    var cluster = {
      account: account,
      name: name,
      serverGroups: [],
    };

    cluster.addServerGroups = function(region, name)  {
      var sg = {
        account: account,
        region: region,
        name: name,
        instances: [],
      };
      this.serverGroups.push(sg);
      return this;
    };

    cluster.addInstance =  function(id, az) {
      var instance = {
        availabilityZone: az,
        id: id,
        provider: 'aws',
        serverGroup: cluster.name + '-v000',
      };

      this.serverGroups[0].instances.push(instance);
      return this;
    };

    return cluster;
  }


});

