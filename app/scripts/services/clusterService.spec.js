'use strict';

describe('Service: InstanceType', function () {

  beforeEach(loadDeckWithoutCacheInitializer);

  beforeEach(inject(function (_clusterService_) {
    this.clusterService = _clusterService_;

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

        this.clusterService.addTasksToServerGroups(app);
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

        this.clusterService.addTasksToServerGroups(app);
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

        this.clusterService.addTasksToServerGroups(app);
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

        this.clusterService.addTasksToServerGroups(app);
        expect(app.serverGroups[0].runningTasks.length).toBe(0);
        expect(app.serverGroups[1].runningTasks.length).toBe(0);
        expect(app.serverGroups[2].runningTasks.length).toBe(0);
        expect(app.serverGroups[3].runningTasks.length).toBe(0);
        expect(app.serverGroups[4].runningTasks.length).toBe(0);
      });
    });

    describe('terminateinstances', function() {
      it ('finds instance within server group', function() {
        var app = this.application;
        app.serverGroups[2].instances = [
          { id: 'in-1' },
          { id: 'in-2' },
        ];
        app.serverGroups[4].instances = [
          { id: 'in-3'}
        ];
        app.tasks = [
          this.buildTask({status: 'RUNNING', variables: [
            { key: 'notification.type', value: 'terminateinstances'},
            { key: 'credentials', value: 'test'},
            { key: 'region', value: 'us-east-1'},
            { key: 'instanceids', value: ['in-2']}
          ]})
        ];

        this.clusterService.addTasksToServerGroups(app);
        expect(app.serverGroups[0].runningTasks.length).toBe(0);
        expect(app.serverGroups[1].runningTasks.length).toBe(0);
        expect(app.serverGroups[2].runningTasks.length).toBe(1);
        expect(app.serverGroups[3].runningTasks.length).toBe(0);
        expect(app.serverGroups[4].runningTasks.length).toBe(0);

      });

      describe('resizeasg, disableasg, destroyasg, enableasg', function() {
        beforeEach(function() {
          this.validateTaskAttached = function() {
            var app = this.application;
            this.clusterService.addTasksToServerGroups(app);
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
          this.clusterService.addTasksToServerGroups(app);
          app.serverGroups.forEach(function(serverGroup) {
            expect(serverGroup.runningTasks.length).toBe(0);
          });
        });

        it('some unknown task', function() {
          var app = this.application;
          this.buildCommonTask('someuknownthing');
          this.clusterService.addTasksToServerGroups(app);
          app.serverGroups.forEach(function(serverGroup) {
            expect(serverGroup.runningTasks.length).toBe(0);
          });
        });
      });


    });
  });


});
