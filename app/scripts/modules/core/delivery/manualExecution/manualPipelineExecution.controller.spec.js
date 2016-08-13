'use strict';

describe('Controller: ManualPipelineExecution', function () {

  beforeEach(
    window.module(
      require('./manualPipelineExecution.controller')
    )
  );

  beforeEach(window.inject(function ($controller, $rootScope, _, $q, _notificationService_) {
    this.scope = $rootScope.$new();
    this.$q = $q;
    this.notificationService = _notificationService_;

    this.initializeController = function(application, pipeline, modalInstance, pipelineConfig) {
      this.ctrl = $controller('ManualPipelineExecutionCtrl', {
        $scope: this.scope,
        application: application,
        pipeline: pipeline,
        pipelineConfig: pipelineConfig,
        _: _,
        $uibModalInstance: modalInstance || {},
        notificationService: this.notificationService,
      });
    };
  }));

  describe('Initialization', function () {
    describe('No pipeline provided', function () {
      it('sets pipeline options on controller from application, skipping disabled', function () {
        let application = {
          pipelineConfigs: { data: [
            { id: 'a' },
            { id: 'b' },
            { id: 'c', disabled: true }
          ]}
        };
        this.initializeController(application);

        expect(this.ctrl.pipelineOptions).toEqual([ {id: 'a'}, {id: 'b'} ]);
      });
    });

    describe('Pipeline provided', function () {
      it ('sets running executions on ctrl', function () {
        let application = {
          pipelineConfigs: { data: [
            { id: 'a', triggers: [], stages: [] },
            { id: 'b' }
          ]},
          executions: { data: [
            { pipelineConfigId: 'a', isActive: false },
            { pipelineConfigId: 'a', isActive: true },
            { pipelineConfigId: 'b', isActive: true },
          ]}
        };

        this.initializeController(application, application.pipelineConfigs.data[0]);
        expect(this.ctrl.currentlyRunningExecutions).toEqual([application.executions.data[1]]);
      });

      it('sets showRebakeOption if any stage is a bake stage', function () {
        let application = {
          pipelineConfigs: { data: [
            {
              id: 'a',
              triggers: [],
              stages: [ {type: 'not-a-bake'}, {type: 'also-not-a-bake'}]
            },
            {
              id: 'b',
              triggers: [],
              stages: [ {type: 'not-a-bake'}, {type: 'bake'}]
            },
          ]},
          executions: { data: []}
        };

        this.initializeController(application, application.pipelineConfigs.data[0]);
        expect(this.ctrl.showRebakeOption).toBe(false);
        this.initializeController(application, application.pipelineConfigs.data[1]);
        expect(this.ctrl.showRebakeOption).toBe(true);
      });

      it('sets parameters if present', function () {
        let application = {
          pipelineConfigs: { data: [
            {
              id: 'a',
              triggers: [],
              stages: [],
              parameterConfig: [
                { name: 'foo' },
                { name: 'bar', 'default': 'mr. peanutbutter' },
                { name: 'baz', 'default': '' },
                { name: 'bojack', 'default': null }
              ]
            },
          ]},
          executions: { data: []}
        };

        this.initializeController(application, application.pipelineConfigs.data[0]);
        expect(this.ctrl.parameters).toEqual({foo: undefined, bar: 'mr. peanutbutter', baz: '', bojack: null});
      });
    });
  });

  describe('notifications', function () {
    it('merges notifications from application and pipeline config', function () {
      let notifications = [
        {type: 'email', address: 'example@spinnaker.io'},
        {type: 'email', address: 'example2@spinnaker.io'},
        {type: 'slack', address: 'spinnaker'},
        {type: 'email', address: 'pipeline@spinnaker.io'},
      ];
      spyOn(this.notificationService, 'getNotificationsForApplication').and.returnValue(this.$q.when({
        application: 'myapp',
        email: [ notifications[0], notifications[1] ],
        slack: [ notifications[2] ]
      }));
      let application = {
        pipelineConfigs: { data: [
          { id: 'a', name: 'aa', triggers: [], stages: [], notifications: [
            notifications[3]
          ]},
        ]},
        executions: { data: []}
      };
      this.initializeController(application, application.pipelineConfigs.data[0], this.modalInstance);
      this.scope.$digest();
      expect(this.ctrl.notifications).toEqual(notifications);
    });

    it('updates notifications when pipeline changes', function () {
      let notifications = [
        {type: 'email', address: 'pipeline@spinnaker.io'},
        {type: 'email', address: 'example2@spinnaker.io'},
        {type: 'slack', address: 'spinnaker'},
      ];
      spyOn(this.notificationService, 'getNotificationsForApplication').and.returnValue(this.$q.when({
        application: 'myapp',
        email: [ notifications[1] ],
      }));
      let application = {
        pipelineConfigs: { data: [
          { id: 'a', name: 'aa', triggers: [], stages: [], notifications: [
            notifications[0]
          ]},
          { id: 'b', name: 'aa', triggers: [], stages: [], notifications: [
            notifications[2]
          ]},
        ]},
        executions: { data: []}
      };
      this.initializeController(application, application.pipelineConfigs.data[0], this.modalInstance);
      this.scope.$digest();
      expect(this.ctrl.notifications).toEqual([notifications[1], notifications[0]]);
      this.ctrl.command.pipeline = application.pipelineConfigs.data[1];
      this.ctrl.pipelineSelected();
      expect(this.ctrl.notifications).toEqual([notifications[1], notifications[2]]);
    });
  });

  describe('execution', function () {
    beforeEach(function () {
      this.command = null;
      this.modalInstance = {
        close: (cmd) => {
          this.command = cmd;
        }
      };
    });
    it('adds a placeholder trigger if none present', function () {
      let application = {
        pipelineConfigs: { data: [
          { id: 'a', name: 'aa', triggers: [], stages: []},
        ]},
        executions: { data: []}
      };
      this.initializeController(application, application.pipelineConfigs.data[0], this.modalInstance);

      this.ctrl.execute();
      expect(this.command.trigger).toEqual({type: 'manual'});
    });

    it('adds parameters if configured', function () {
      let application = {
        pipelineConfigs: { data: [
          {
            id: 'a',
            triggers: [],
            stages: [],
            parameterConfig: [
              { name: 'bar', 'default': 'mr. peanutbutter' },
            ]
          },
        ]},
        executions: { data: []}
      };

      this.initializeController(application, application.pipelineConfigs.data[0], this.modalInstance);
      this.ctrl.execute();
      expect(this.command.trigger.parameters).toEqual({bar: 'mr. peanutbutter'});
    });

  });
});
