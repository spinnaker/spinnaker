'use strict';


describe('Controller: ManualPipelineExecution', function () {

  beforeEach(
    window.module(
      require('./manualPipelineExecution.controller')
    )
  );

  beforeEach(window.inject(function ($controller, $rootScope, igorService, _, $q) {
    this.scope = $rootScope.$new();
    this.igorService = igorService;
    this.$q = $q;

    this.initializeController = function(application, pipeline, modalInstance) {
      this.ctrl = $controller('ManualPipelineExecutionCtrl', {
        $scope: this.scope,
        application: application,
        pipeline: pipeline,
        igorService: igorService,
        _: _,
        $modalInstance: modalInstance || {}
      });
    };
  }));

  describe('Initialization', function () {
    describe('No pipeline provided', function () {
      it('sets pipeline options on controller from application', function () {
        let application = {
          pipelineConfigs: [
            { id: 'a' },
            { id: 'b' }
          ]
        };
        this.initializeController(application);

        expect(this.ctrl.pipelineOptions).toBe(application.pipelineConfigs);
      });
    });

    describe('Pipeline provided', function () {
      it ('sets running executions on ctrl', function () {
        let application = {
          pipelineConfigs: [
            { id: 'a', triggers: [], stages: [] },
            { id: 'b' }
          ],
          executions: [
            { pipelineConfigId: 'a', isActive: false },
            { pipelineConfigId: 'a', isActive: true },
            { pipelineConfigId: 'b', isActive: true },
          ]
        };

        this.initializeController(application, application.pipelineConfigs[0]);
        expect(this.ctrl.currentlyRunningExecutions).toEqual([application.executions[1]]);
      });

      it('adds jenkins trigger options to ctrl, setting description, overriding type, preferring enabled', function () {
        let application = {
          pipelineConfigs: [
            {
              id: 'a',
              triggers: [
                { type: 'jenkins', enabled: false, master: 'spinnaker', job: 'package'},
                { type: 'jenkins', enabled: true, master: 'spinnaker', job: 'test'},
                { type: 'other'}
              ],
              stages: []
            },
          ],
          executions: []
        };
        let expected = [
          { type: 'manual', master: 'spinnaker', job: 'test', description: 'spinnaker: test', buildNumber: null, enabled: true },
          { type: 'manual', master: 'spinnaker', job: 'package', description: 'spinnaker: package', buildNumber: null, enabled: false }
        ];

        this.initializeController(application, application.pipelineConfigs[0]);
        expect(this.ctrl.triggers).toEqual(expected);
        expect(this.ctrl.command.trigger).toEqual(expected[0]);
      });

      it('includes completed, successful jobs for selected trigger', function () {
        let builds = [
          { building: true, number: 5 },
          { building: false, result: 'FAILURE', number: 4 },
          { building: false, result: 'SUCCESS', number: 3 },
          { building: false, result: 'SUCCESS', number: 2 },
        ];
        spyOn(this.igorService, 'listBuildsForJob').and.returnValue(this.$q.when(builds));

        let application = {
          pipelineConfigs: [
            {
              id: 'a',
              triggers: [ { type: 'jenkins', enabled: true, master: 'spinnaker', job: 'test'} ],
              stages: []
            },
          ],
          executions: []
        };

        this.initializeController(application, application.pipelineConfigs[0]);
        expect(this.igorService.listBuildsForJob.calls.count()).toBe(1);
        expect(this.ctrl.viewState.buildsLoading).toBe(true);

        this.scope.$digest();
        expect(this.ctrl.builds).toEqual([builds[2], builds[3]]);
        expect(this.ctrl.command.selectedBuild).toEqual(builds[2]);
        expect(this.ctrl.viewState.buildsLoading).toBe(false);
      });

      it('clears builds when trigger is unselected', function () {
        spyOn(this.igorService, 'listBuildsForJob').and.returnValue(this.$q.when([
          { building: false, result: 'SUCCESS', number: 3 }
        ]));
        let application = {
          pipelineConfigs: [
            {
              id: 'a',
              triggers: [ { type: 'jenkins', enabled: true, master: 'spinnaker', job: 'test'} ],
              stages: []
            },
          ],
          executions: []
        };

        this.initializeController(application, application.pipelineConfigs[0]);
        this.scope.$digest();
        expect(this.ctrl.builds.length).toBe(1);
        this.ctrl.triggerUpdated(null);
        expect(this.ctrl.builds.length).toBe(0);
      });

      it('sets showRebakeOption if any stage is a bake stage', function () {
        let application = {
          pipelineConfigs: [
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
          ],
          executions: []
        };

        this.initializeController(application, application.pipelineConfigs[0]);
        expect(this.ctrl.showRebakeOption).toBe(false);
        this.initializeController(application, application.pipelineConfigs[1]);
        expect(this.ctrl.showRebakeOption).toBe(true);
      });

      it('sets parameters if present', function () {
        let application = {
          pipelineConfigs: [
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
          ],
          executions: []
        };

        this.initializeController(application, application.pipelineConfigs[0]);
        expect(this.ctrl.parameters).toEqual({foo: undefined, bar: 'mr. peanutbutter', baz: '', bojack: null});
      });
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
        pipelineConfigs: [
          { id: 'a', name: 'aa', triggers: [], stages: []},
        ],
        executions: []
      };
      this.initializeController(application, application.pipelineConfigs[0], this.modalInstance);

      this.ctrl.execute();
      expect(this.command.trigger).toEqual({});
    });

    it('adds parameters if configured', function () {
      let application = {
        pipelineConfigs: [
          {
            id: 'a',
            triggers: [],
            stages: [],
            parameterConfig: [
              { name: 'bar', 'default': 'mr. peanutbutter' },
            ]
          },
        ],
        executions: []
      };

      this.initializeController(application, application.pipelineConfigs[0], this.modalInstance);
      this.ctrl.execute();
      expect(this.command.trigger.parameters).toEqual({bar: 'mr. peanutbutter'});
    });

    it('adds build number if selected', function () {
      spyOn(this.igorService, 'listBuildsForJob').and.returnValue(this.$q.when([
        { building: false, result: 'SUCCESS', number: 3 }
      ]));
      let application = {
        pipelineConfigs: [
          {
            id: 'a',
            triggers: [ { type: 'jenkins', enabled: true, master: 'spinnaker', job: 'test'} ],
            stages: []
          },
        ],
        executions: []
      };

      this.initializeController(application, application.pipelineConfigs[0], this.modalInstance);
      this.scope.$digest();
      this.ctrl.execute();
      expect(this.command.trigger.buildNumber).toBe(3);
    });
  });
});
