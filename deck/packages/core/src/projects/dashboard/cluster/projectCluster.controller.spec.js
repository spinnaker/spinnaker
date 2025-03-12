'use strict';

describe('Controller: projectCluster directive', function () {
  var $controller, $scope, project, cluster;

  beforeEach(window.module(require('./projectCluster.directive').name));

  beforeEach(
    window.inject(function ($rootScope, _$controller_) {
      $scope = $rootScope.$new();
      $controller = _$controller_;
    }),
  );

  describe('model construction', function () {
    beforeEach(function () {
      cluster = {
        account: 'prod',
      };
      project = {
        name: 'test-app',
        config: {
          applications: ['app1'],
          clusters: [cluster],
        },
      };

      this.initialize = () => {
        $controller(
          'ProjectClusterCtrl',
          {
            $scope: $scope,
          },
          {
            project: project,
            cluster: cluster,
          },
        ).$onInit();
      };
    });

    it('derives regions for cluster from application clusters', function () {
      cluster = {
        applications: [
          {
            clusters: [{ region: 'us-east-1' }],
          },
          {
            clusters: [{ region: 'us-west-1' }, { region: 'us-east-1' }],
          },
        ],
      };

      this.initialize();
      expect(cluster.regions).toEqual(['us-east-1', 'us-west-1']);
      cluster.applications.push({ clusters: [{ region: 'eu-west-1' }] });

      this.initialize();
      expect(cluster.regions).toEqual(['eu-west-1', 'us-east-1', 'us-west-1']);
    });

    it('puts application clusters into regions map', function () {
      let cluster1 = { region: 'us-east-1' },
        cluster2 = { region: 'us-west-1' },
        cluster3 = { region: 'us-east-1' };

      cluster = {
        applications: [{ clusters: [cluster1] }, { clusters: [cluster2, cluster3] }],
      };

      this.initialize();
      expect(cluster.applications[0].regions['us-east-1']).toBe(cluster1);
      expect(cluster.applications[0].regions['us-west-1']).toBeUndefined();
      expect(cluster.applications[1].regions['us-west-1']).toBe(cluster2);
      expect(cluster.applications[1].regions['us-east-1']).toBe(cluster3);
    });

    it('adds application build if any present', function () {
      let cluster1 = { region: 'us-east-1', builds: [] },
        cluster2 = { region: 'us-west-1', builds: [{ buildNumber: 1 }] },
        cluster3 = { region: 'us-east-1', builds: [{ buildNumber: 1 }] };

      cluster = {
        applications: [{ clusters: [cluster1] }, { clusters: [cluster2, cluster3] }],
      };

      this.initialize();
      expect(cluster.applications[0].build).toBeUndefined();
      expect(cluster.applications[1].build).not.toBeUndefined();
      expect(cluster.applications[1].build.buildNumber).toBe(1);
    });

    it('adds inconsistentBuilds flag to cluster and application clusters', function () {
      let cluster1 = { region: 'us-east-1', builds: [] },
        cluster2 = { region: 'us-west-1', builds: [{ buildNumber: 1 }] },
        cluster3 = { region: 'us-east-1', builds: [{ buildNumber: 1 }, { buildNumber: 3 }] },
        cluster4 = { region: 'us-east-1', builds: [{ buildNumber: 2 }] };

      cluster = {
        applications: [{ clusters: [cluster1] }, { clusters: [cluster2, cluster3] }, { clusters: [cluster4] }],
      };

      this.initialize();
      expect(cluster.applications[0].build).toBeUndefined();
      expect(cluster.applications[0].hasInconsistentBuilds).toBeUndefined();
      expect(cluster.applications[1].build).not.toBeUndefined();
      expect(cluster.applications[1].build.buildNumber).toBe(3);
      expect(cluster.applications[1].hasInconsistentBuilds).toBe(true);
      expect(cluster.applications[2].build).not.toBeUndefined();
      expect(cluster.applications[2].build.buildNumber).toBe(2);
      expect(cluster.applications[2].hasInconsistentBuilds).toBe(false);
    });
  });
});
