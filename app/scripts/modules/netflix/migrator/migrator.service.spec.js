'use strict';

describe('Service: migrator', function () {

  var service, $q, $scope, taskExecutor;

  beforeEach(
    window.module(
      require('./migrator.service')
    )
  );

  beforeEach(
    window.inject(function (_migratorService_, _$q_, _taskExecutor_, $rootScope) {
      service = _migratorService_;
      $q = _$q_;
      taskExecutor = _taskExecutor_;
      $scope = $rootScope.$new();
    })
  );

  describe('getPreview', function () {
    beforeEach(function () {
      this.executeMigration = function(task) {
        spyOn(taskExecutor, 'executeTask').and.returnValue($q.when(task));

        let result = null;
        service.executeMigration({}, {type: task.type}).then((taskResult) => result = taskResult);
        $scope.$digest();

        return result.getPreview();
      };
    });

    it ('extracts security groups, load balancers, server groups', function () {
      let task = {
        type: 'migrateServerGroup',
        getValueFor: (key) => {
          if (key === 'kato.tasks') {
            return [
              { resultObjects: [
                { type: 'should be ignored' },
                {
                  serverGroupNames: [ 'migrated-v001' ],
                  securityGroups: [
                    {
                      target: { targetName: 'sg-1', credentials: 'prod' },
                      created: [{ targetName: 'sg-1', credentials: 'prod' }]
                    }
                  ],
                  loadBalancers: [
                    { targetName: 'lb-1', securityGroups: [
                      {
                        target: { targetName: 'sg-2', credentials: 'prod' },
                        created: [{targetName: 'sg-2', credentials: 'prod' }]
                      }
                    ]}
                  ]
                }
              ]}
            ];
          }
          return null;
        }
      };

      let preview = this.executeMigration(task);

      expect(preview.securityGroups[0].targetName).toBe('sg-1');
      expect(preview.securityGroups[1].targetName).toBe('sg-2');
      expect(preview.loadBalancers[0].targetName).toBe('lb-1');
      expect(preview.serverGroupNames[0]).toBe('migrated-v001');
    });

    it ('returns an empty object when no kato task present', function () {
      let task = {
        type: 'migrateServerGroup',
        getValueFor: () => null
      };

      let preview = this.executeMigration(task);

      expect(preview).toEqual({});
    });

    it ('returns an empty object when kato task is empty', function () {
      let task = {
        type: 'migrateServerGroup',
        getValueFor: () => {
          return {};
        }
      };

      let preview = this.executeMigration(task);

      expect(preview).toEqual({});

    });

  });

  describe('getEventLog', function () {
    beforeEach(function () {
      this.getEventLog = function(task) {
        spyOn(taskExecutor, 'executeTask').and.returnValue($q.when(task));

        let result = null;
        service.executeMigration({}, {type: task.type}).then((taskResult) => result = taskResult);
        $scope.$digest();

        return result.getEventLog();
      };
    });

    it('returns event messages sorted by timestamp', function () {
      let task = {
        getValueFor: (key) => {
          if (key === 'kato.tasks') {
            return [{
              history: [
                { status: 'first' },
                { status: 'second' },
                { status: 'third' },
              ]
            }];
          }
          return null;
        }
      };

      let log = this.getEventLog(task);

      expect(log).toEqual(['first', 'second', 'third']);
    });
  });

});
