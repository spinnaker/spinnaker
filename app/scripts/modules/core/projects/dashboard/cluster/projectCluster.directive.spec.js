'use strict';

describe('Controller: projectCluster directive', function () {

  var $controller, vm, $scope, project, cluster, clusterService, $q, urlBuilder;

  beforeEach(window.module(
    require('./projectCluster.directive.js')
  ));

  beforeEach(
    window.inject(function ($rootScope, _$controller_, _$q_, _clusterService_, _urlBuilderService_) {
      $scope = $rootScope.$new();
      $controller = _$controller_;
      $q = _$q_;
      clusterService = _clusterService_;
      urlBuilder = _urlBuilderService_;
    })
  );

  describe('model construction', function () {
    beforeEach( function() {
      spyOn(urlBuilder, 'buildFromMetadata').and.returnValue('url');

      cluster = {
        account: 'prod'
      };
      project = {
        name: 'test-app',
        config: {
          applications: [ 'app1' ],
          clusters: [cluster],
        },
      };

      this.initialize = () => {
        vm = $controller('ProjectClusterCtrl', {
          $scope: $scope,
          $q: $q,
          clusterService: clusterService,
        }, {project: project, cluster: cluster});
      };
    });

    it('should add instance counts, last created time, and build data to model', function () {
      spyOn(clusterService, 'getCluster').and.returnValue($q.when({
        serverGroups: [
          {
            name: 'app1-v000',
            region: 'us-east-1',
            createdTime: 1,
            buildInfo: {
              jenkins: { number: 3 }
            },
            instanceCounts: {
              up: 3, down: 1, unknown: 1, outOfService: 1, starting: 2, total: 8,
            },
            instances: [ { } ],
          },
          {
            name: 'app1-v001',
            region: 'us-east-1',
            createdTime: 2,
            buildInfo: {
              jenkins: { number: 3 }
            },
            instanceCounts: {
              up: 1, down: 0, unknown: 1, outOfService: 2, starting: 1, total: 5,
            },
            instances: [ { } ],
          },
          {
            name: 'app1-v003',
            region: 'us-west-1',
            createdTime: 24,
            buildInfo: {
              jenkins: { number: 3 }
            },
            instanceCounts: {
              up: 1, down: 0, unknown: 0, outOfService: 0, starting: 1, total: 2,
            },
            instances: [ { } ],
          }
        ]
      }));

      this.initialize();
      $scope.$digest();
      let clusterData = vm.clusterData,
          usEast = clusterData.applications[0].regions['us-east-1'],
          usWest = clusterData.applications[0].regions['us-west-1'];

      expect(clusterData.instanceCounts).toEqual({total: 15, up: 5, down: 1, unknown: 2, outOfService: 3, starting: 4});
      expect(usEast.instanceCounts).toEqual({total: 13, up: 4, down: 1, unknown: 2, outOfService: 3, starting: 3});
      expect(usWest.instanceCounts).toEqual({total: 2, up: 1, down: 0, unknown: 0, outOfService: 0, starting: 1});

      expect(clusterData.applications[0].lastCreatedTime).toBe(24);
      expect(usEast.lastCreatedTime).toBe(2);
      expect(usWest.lastCreatedTime).toBe(24);
      expect(vm.clusterData.applications[0].build.number).toBe(3);
    });

    it('should add inconsistent builds to model', function() {
      spyOn(clusterService, 'getCluster').and.returnValue($q.when({
        serverGroups: [
          {
            name: 'app1-v000',
            region: 'us-east-1',
            createdTime: 1,
            buildInfo: {
              jenkins: { number: 3 }
            },
            instanceCounts: {
              up: 1, down: 0, unknown: 0, outOfService: 0, starting: 0, total: 1,
            },
            instances: [ { } ],
          },
          {
            name: 'app1-v001',
            region: 'us-east-1',
            createdTime: 2,
            buildInfo: {
              jenkins: { number: 4 }
            },
            instanceCounts: {
              up: 1, down: 0, unknown: 0, outOfService: 0, starting: 0, total: 1,
            },
            instances: [ { } ],
          }
        ]
      }));

      this.initialize();
      $scope.$digest();
      expect(vm.clusterData.applications[0].hasInconsistentBuilds).toBe(true);
      expect(vm.clusterData.applications[0].regions['us-east-1'].inconsistentBuilds[0].number).toBe(3);

    });

    it('should only add inconsistent build to model once', function () {
      spyOn(clusterService, 'getCluster').and.returnValue($q.when({
        serverGroups: [
          {
            name: 'app1-v000',
            region: 'us-east-1',
            createdTime: 1,
            buildInfo: {
              jenkins: { number: 3 }
            },
            instanceCounts: {
              up: 1, down: 0, unknown: 0, outOfService: 0, starting: 0, total: 1,
            },
            instances: [ { } ],
          },
          {
            name: 'app1-v001',
            region: 'us-east-1',
            createdTime: 2,
            buildInfo: {
              jenkins: { number: 3 }
            },
            instanceCounts: {
              up: 1, down: 0, unknown: 0, outOfService: 0, starting: 0, total: 1,
            },
            instances: [ { } ],
          },
          {
            name: 'app1-v002',
            region: 'us-east-1',
            createdTime: 3,
            buildInfo: {
              jenkins: { number: 4 }
            },
            instanceCounts: {
              up: 1, down: 0, unknown: 0, outOfService: 0, starting: 0, total: 1,
            },
            instances: [ { } ],
          }
        ]
      }));

      this.initialize();
      $scope.$digest();
      expect(vm.clusterData.applications[0].hasInconsistentBuilds).toBe(true);
      expect(vm.clusterData.applications[0].regions['us-east-1'].inconsistentBuilds.length).toBe(1);
      expect(vm.clusterData.applications[0].regions['us-east-1'].inconsistentBuilds[0].number).toBe(3);
    });

    it('should add inconsistent build, even if it is in a more recent deployment', function () {
      spyOn(clusterService, 'getCluster').and.returnValue($q.when({
        serverGroups: [
          {
            name: 'app1-v000',
            region: 'us-east-1',
            createdTime: 1,
            buildInfo: {
              jenkins: { number: 4 }
            },
            instanceCounts: {
              up: 1, down: 0, unknown: 0, outOfService: 0, starting: 0, total: 1,
            },
            instances: [ { } ],
          },
          {
            name: 'app1-v002',
            region: 'us-east-1',
            createdTime: 3,
            buildInfo: {
              jenkins: { number: 2 }
            },
            instanceCounts: {
              up: 1, down: 0, unknown: 0, outOfService: 0, starting: 0, total: 1,
            },
            instances: [ { } ],
          }
        ]
      }));

      this.initialize();
      $scope.$digest();
      expect(vm.clusterData.applications[0].hasInconsistentBuilds).toBe(true);
      expect(vm.clusterData.applications[0].regions['us-east-1'].inconsistentBuilds.length).toBe(1);
      expect(vm.clusterData.applications[0].regions['us-east-1'].inconsistentBuilds[0].number).toBe(2);
    });
  });
});
