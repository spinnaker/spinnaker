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
        service.executeMigration({}).then((taskResult) => result = taskResult);
        $scope.$digest();

        return result.getPreview();
      };
    });

    it ('extracts security groups, load balancers, server groups', function () {
      let task = {
        getValueFor: (key) => {
          if (key === 'tide.task') {
            return {
              mutations: [
                { mutationDetails: { awsReference: { identity: { groupName: 'sg-1' } } } },
                { mutationDetails: { awsReference: { identity: { loadBalancerName: 'lb-1' } } } },
                { mutationDetails: { awsReference: { identity: { autoScalingGroupName: 'asg-1' } } } },
              ]
            };
          }
          return null;
        }
      };

      let preview = this.executeMigration(task);

      expect(preview.securityGroups[0].mutationDetails.awsReference.identity.groupName).toBe('sg-1');
      expect(preview.serverGroups[0].mutationDetails.awsReference.identity.autoScalingGroupName).toBe('asg-1');
      expect(preview.loadBalancers[0].mutationDetails.awsReference.identity.loadBalancerName).toBe('lb-1');
      expect(preview.pipelines).toEqual([]);
    });

    it ('returns an empty preview object when no tide task present', function () {
      let task = {
        getValueFor: () => null
      };

      let preview = this.executeMigration(task);

      expect(preview.securityGroups).toEqual([]);
      expect(preview.serverGroups).toEqual([]);
      expect(preview.loadBalancers).toEqual([]);
      expect(preview.pipelines).toEqual([]);
    });

    it ('returns an empty preview object when tide task is empty', function () {
      let task = {
        getValueFor: () => {
          return {};
        }
      };

      let preview = this.executeMigration(task);

      expect(preview.securityGroups).toEqual([]);
      expect(preview.serverGroups).toEqual([]);
      expect(preview.loadBalancers).toEqual([]);
      expect(preview.pipelines).toEqual([]);

    });

    it ('returns an empty preview object when mutations array is empty', function () {
      let task = {
        getValueFor: () => {
          return { mutations: [] };
        }
      };

      let preview = this.executeMigration(task);

      expect(preview.securityGroups).toEqual([]);
      expect(preview.serverGroups).toEqual([]);
      expect(preview.loadBalancers).toEqual([]);
      expect(preview.pipelines).toEqual([]);

    });
  });

  describe('getEventLog', function () {
    beforeEach(function () {
      this.getEventLog = function(task) {
        spyOn(taskExecutor, 'executeTask').and.returnValue($q.when(task));

        let result = null;
        service.executeMigration({}).then((taskResult) => result = taskResult);
        $scope.$digest();

        return result.getEventLog();
      };
    });

    it('returns event messages sorted by timestamp', function () {
      let task = {
        getValueFor: (key) => {
          if (key === 'tide.task') {
            return {
              history: [
                { timeStamp: 3, message: 'third' },
                { timeStamp: 1, message: 'first' },
                { timeStamp: 2, message: 'second' },
              ]
            };
          }
          return null;
        }
      };

      let log = this.getEventLog(task);

      expect(log).toEqual(['first', 'second', 'third']);
    });
  });

});
