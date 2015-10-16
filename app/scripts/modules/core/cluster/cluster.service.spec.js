'use strict';

describe('Service: InstanceType', function () {
  beforeEach(
    window.module(
      require('./cluster.service.js')
    )
  );

  var clusterService, $q, $scope;

  beforeEach(window.inject(function (_clusterService_, _$q_, $rootScope) {
    clusterService = _clusterService_;
    $q = _$q_;
    $scope = $rootScope.$new();

    this.buildTask = function(config) {
      return {
        status: config.status,
        getValueFor: function(key) {
          return _.find(config.variables, { key: key }) ? _.find(config.variables, { key: key }).value : null;
        }
      };
    };

    this.application = {
      serverGroups: [
        {name: 'the-target', account: 'not-the-target', region: 'us-east-1'},
        {name: 'the-target', account: 'test', region: 'not-the-target'},
        {name: 'the-target', account: 'test', region: 'us-east-1'},
        {name: 'not-the-target', account: 'test', region: 'us-east-1'},
        {name: 'the-source', account: 'test', region: 'us-east-1'}
      ]
    };

  }));

  describe('health count rollups', function() {
    it('aggregates health counts from server groups', function() {
      var application = {
        serverGroups: [
          {cluster: 'cluster-a', name: 'cluster-a-v001', account: 'test', region: 'us-east-1', instances: [], totalCount: 1, upCount: 1 },
          {cluster: 'cluster-a', name: 'cluster-a-v001', account: 'test', region: 'us-west-1', instances: [], totalCount: 2, downCount: 2 },
          {cluster: 'cluster-b', name: 'cluster-b-v001', account: 'test', region: 'us-east-1', instances: [], totalCount: 1, startingCount: 1 },
          {cluster: 'cluster-b', name: 'cluster-b-v001', account: 'test', region: 'us-west-1', instances: [], totalCount: 1, outOfServiceCount: 1 },
          {cluster: 'cluster-b', name: 'cluster-b-v002', account: 'test', region: 'us-west-1', instances: [], totalCount: 2, unknownCount: 1, outOfServiceCount: 1 },
        ]
      };

      var clusters = clusterService.createServerGroupClusters(application.serverGroups);

      expect(clusters.length).toBe(2);
      expect(clusters[0].totalCount).toBe(3);
      expect(clusters[0].upCount).toBe(1);
      expect(clusters[0].downCount).toBe(2);
      expect(clusters[0].startingCount).toBe(0);
      expect(clusters[0].outOfServiceCount).toBe(0);
      expect(clusters[0].unknownCount).toBe(0);

      expect(clusters[1].totalCount).toBe(4);
      expect(clusters[1].upCount).toBe(0);
      expect(clusters[1].downCount).toBe(0);
      expect(clusters[1].startingCount).toBe(1);
      expect(clusters[1].outOfServiceCount).toBe(2);
      expect(clusters[1].unknownCount).toBe(1);

    });
  });

  describe('addTasksToServerGroups', function() {
    describe('createcopylastasg tasks', function() {
      it('attaches to source and target', function() {
        var app = this.application;
        app.tasks = [
          this.buildTask({status: 'RUNNING', variables: [
            { key: 'notification.type', value: 'createcopylastasg'},
            { key: 'deploy.account.name', value: 'test'},
            { key: 'availabilityZones', value: {'us-east-1': ['a']}},
            { key: 'deploy.server.groups', value: {'us-east-1': ['the-target']}},
            { key: 'source', value: { asgName: 'the-source', account: 'test', region: 'us-east-1'}}
          ]})
        ];

        clusterService.addTasksToServerGroups(app);
        expect(app.serverGroups[0].runningTasks.length).toBe(0);
        expect(app.serverGroups[1].runningTasks.length).toBe(0);
        expect(app.serverGroups[2].runningTasks.length).toBe(1);
        expect(app.serverGroups[3].runningTasks.length).toBe(0);
        expect(app.serverGroups[4].runningTasks.length).toBe(1);
      });

      it('still attaches to source when target not found', function() {
        var app = this.application;
        app.tasks = [
          this.buildTask({status: 'RUNNING', variables: [
            { key: 'notification.type', value: 'createcopylastasg'},
            { key: 'deploy.account.name', value: 'test'},
            { key: 'availabilityZones', value: {'us-east-1': ['a']}},
            { key: 'deploy.server.groups', value: {'us-east-1': ['not-found-target']}},
            { key: 'source', value: { asgName: 'the-source', account: 'test', region: 'us-east-1'}}
          ]})
        ];

        clusterService.addTasksToServerGroups(app);
        expect(app.serverGroups[0].runningTasks.length).toBe(0);
        expect(app.serverGroups[1].runningTasks.length).toBe(0);
        expect(app.serverGroups[2].runningTasks.length).toBe(0);
        expect(app.serverGroups[3].runningTasks.length).toBe(0);
        expect(app.serverGroups[4].runningTasks.length).toBe(1);
      });
    });

    describe('createdeploy', function() {
      it('attaches to deployed server group', function() {
        var app = this.application;
        app.tasks = [
          this.buildTask({status: 'RUNNING', variables: [
            { key: 'notification.type', value: 'createdeploy'},
            { key: 'deploy.account.name', value: 'test'},
            { key: 'deploy.server.groups', value: {'us-east-1': ['the-target']}},
          ]})
        ];

        clusterService.addTasksToServerGroups(app);
        expect(app.serverGroups[0].runningTasks.length).toBe(0);
        expect(app.serverGroups[1].runningTasks.length).toBe(0);
        expect(app.serverGroups[2].runningTasks.length).toBe(1);
        expect(app.serverGroups[3].runningTasks.length).toBe(0);
        expect(app.serverGroups[4].runningTasks.length).toBe(0);
      });

      it('does nothing when target not found', function() {
        var app = this.application;
        app.tasks = [
          this.buildTask({status: 'RUNNING', variables: [
            { key: 'notification.type', value: 'createdeploy'},
            { key: 'deploy.account.name', value: 'test'},
            { key: 'deploy.server.groups', value: {'us-east-1': ['not-found-target']}},
          ]})
        ];

        clusterService.addTasksToServerGroups(app);
        expect(app.serverGroups[0].runningTasks.length).toBe(0);
        expect(app.serverGroups[1].runningTasks.length).toBe(0);
        expect(app.serverGroups[2].runningTasks.length).toBe(0);
        expect(app.serverGroups[3].runningTasks.length).toBe(0);
        expect(app.serverGroups[4].runningTasks.length).toBe(0);
      });
    });

    describe('can find task in server groups by instance id', function() {
      _.each([
        'terminateinstances', 'rebootinstances',
        'registerinstanceswithloadbalancer', 'deregisterinstancesfromloadbalancer',
        'enableinstancesindiscovery', 'disableinstancesindiscovery'
        ], function(name) {
        describe(name, function() {
          it ('finds instance within server group (' + name + ')', function() {
            var app = this.application;
            app.serverGroups[2].instances = [
              { id: 'in-1' },
              { id: 'in-2' },
            ];
            app.serverGroups[4].instances = [
              { id: 'in-3'},
              { id: 'in-2'},
            ];
            app.tasks = [
              this.buildTask({status: 'RUNNING', variables: [
                { key: 'notification.type', value: name},
                { key: 'credentials', value: 'test'},
                { key: 'region', value: 'us-east-1'},
                { key: 'instanceIds', value: ['in-2']}
              ]})
            ];

            clusterService.addTasksToServerGroups(app);
            expect(app.serverGroups[0].runningTasks.length).toBe(0);
            expect(app.serverGroups[1].runningTasks.length).toBe(0);
            expect(app.serverGroups[2].runningTasks.length).toBe(1);
            expect(app.serverGroups[3].runningTasks.length).toBe(0);
            expect(app.serverGroups[4].runningTasks.length).toBe(1);
          });
        });
      });
    });

    describe('resizeasg, disableasg, destroyasg, enableasg', function() {
      beforeEach(function() {
        this.validateTaskAttached = function() {
          var app = this.application;
          clusterService.addTasksToServerGroups(app);
          expect(app.serverGroups[0].runningTasks.length).toBe(0);
          expect(app.serverGroups[1].runningTasks.length).toBe(0);
          expect(app.serverGroups[2].runningTasks.length).toBe(1);
          expect(app.serverGroups[3].runningTasks.length).toBe(0);
          expect(app.serverGroups[4].runningTasks.length).toBe(0);
        };

        this.buildCommonTask = function(type) {
          this.application.tasks = [
            this.buildTask({status: 'RUNNING', variables: [
              { key: 'notification.type', value: type},
              { key: 'credentials', value: 'test'},
              { key: 'regions', value: ['us-east-1']},
              { key: 'asgName', value: 'the-target'},
            ]})
          ];
        };
      });

      it('resizeasg', function() {
        this.buildCommonTask('resizeasg');
        this.validateTaskAttached();
      });

      it('disableasg', function() {
        this.buildCommonTask('resizeasg');
        this.validateTaskAttached();
      });

      it('destroyasg', function() {
        this.buildCommonTask('resizeasg');
        this.validateTaskAttached();
      });

      it('enableasg', function() {
        this.buildCommonTask('resizeasg');
        this.validateTaskAttached();
      });

      it('ignores non-running tasks', function() {
        var app = this.application;
        this.buildCommonTask('resizeasg');
        app.tasks[0].status = 'COMPLETED';
        clusterService.addTasksToServerGroups(app);
        app.serverGroups.forEach(function(serverGroup) {
          expect(serverGroup.runningTasks.length).toBe(0);
        });
      });

      it('some unknown task', function() {
        var app = this.application;
        this.buildCommonTask('someuknownthing');
        clusterService.addTasksToServerGroups(app);
        app.serverGroups.forEach(function(serverGroup) {
          expect(serverGroup.runningTasks.length).toBe(0);
        });
      });
    });


    describe('extraction region from stage context', function () {

      it('should return the region from the deploy.server.groups node', function () {

        var context = {
          'deploy.server.groups':  {
            'us-west-1': ['mahe-prestaging-v001']
          }
        };

        var result = clusterService.extractRegionFromContext(context);
        expect(result).toBe('us-west-1');

      });


      it('should return "undefined" if nothing is extracted', function () {
        var context = {};

        var result = clusterService.extractRegionFromContext(context);

        expect(result).toBeUndefined();
      });

    });


    describe('add executions to server group for deploy stage', function () {
      var application = {};

      beforeEach(function() {
        application.serverGroups = [
          {
            name: 'foo-v001',
            account: 'test',
            region: 'us-west-1'
          }
        ];
      });

      it('should successfully add a matched execution to a server group', function () {
        var executions = [
          {
            stages: [
              {
                type: 'deploy',
                context: {
                  'deploy.server.groups':  {
                    'us-west-1': ['foo-v001']
                  },
                  account:'test',
                }
              }
            ]
          }
        ];

        application.executions = executions;
        var result = clusterService.addExecutionsToServerGroups(application);

        expect(result.serverGroups[0].executions.length).toBe(1);
      });


      it('should NOT add a execution to a server group if the region does not match', function () {
        var executions = [
          {
            stages: [
              {
                type: 'deploy',
                context: {
                  'deploy.server.groups':  {
                    'us-east-1': ['foo-v001']
                  },
                  account:'test',
                }
              }
            ]
          }
        ];

        application.executions = executions;
        var result = clusterService.addExecutionsToServerGroups(application);

        expect(result.serverGroups[0].executions.length).toBe(0);
      });


      it('should NOT add a execution to a server group if the account does not match', function () {
        var executions = [
          {
            stages: [
              {
                type: 'deploy',
                context: {
                  'deploy.server.groups':  {
                    'us-west-1': ['foo-v001']
                  },
                  account:'prod',
                }
              }
            ]
          }
        ];

        application.executions = executions;
        var result = clusterService.addExecutionsToServerGroups(application);

        expect(result.serverGroups[0].executions.length).toBe(0);
      });
    });


    describe('add executions to server group for disableAsg stage', function () {
      var application = {};

      beforeEach(function() {
        application.serverGroups = [
          {
            name: 'foo-v001',
            account: 'test',
            region: 'us-west-1'
          }
        ];
      });

      it('should successfully add a matched execution to a server group', function () {
        var executions = [
          {
            stages: [
              {
                type: 'disableAsg',
                context: {
                  'targetop.asg.disableAsg.name': 'foo-v001',
                  'targetop.asg.disableAsg.regions': ['us-west-1'],
                  credentials: 'test',
                }
              }
            ]
          }
        ];

        application.executions = executions;
        var result = clusterService.addExecutionsToServerGroups(application);

        expect(result.serverGroups[0].executions.length).toBe(1);
      });


      it('should NOT add a execution to a server group if the region does not match', function () {
        var executions = [
          {
            stages: [
              {
                type: 'disableAsg',
                context: {
                  'targetop.asg.disableAsg.name': 'foo-v001',
                  'targetop.asg.disableAsg.regions': ['us-east-1'],
                  credentials: 'test',
                }
              }
            ]
          }
        ];

        application.executions = executions;
        var result = clusterService.addExecutionsToServerGroups(application);

        expect(result.serverGroups[0].executions.length).toBe(0);
      });


      it('should NOT add a execution to a server group if the account does not match', function () {
        var executions = [
          {
            stages: [
              {
                type: 'deploy',
                context: {
                  'targetop.asg.disableAsg.name': 'foo-v001',
                  'targetop.asg.disableAsg.regions': ['us-west-1'],
                  credentials: 'prod',
                }
              }
            ]
          }
        ];

        application.executions = executions;
        var result = clusterService.addExecutionsToServerGroups(application);

        expect(result.serverGroups[0].executions.length).toBe(0);
      });
    });
  });
});
