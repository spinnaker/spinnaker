import { IController, IControllerService, IQService, IRootScopeService, IScope, IWindowService } from 'angular';

import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { PipelineConfigService } from './services/PipelineConfigService';
import {
  IDockerTrigger,
  IGitTrigger,
  INotification,
  IParameter,
  IPipelineTemplateConfigV2,
  IPipelineTemplatePlanV2,
} from '../../domain';

const githubTrigger: IGitTrigger = {
  branch: 'master',
  enabled: true,
  project: 'spinnaker',
  slug: 'test-project',
  source: 'github',
  type: 'git',
};

const inheritedDockerTrigger: IDockerTrigger = {
  account: 'test-docker-registry',
  enabled: true,
  inherited: true,
  organization: 'test-org',
  registry: 'index.docker.io',
  repository: 'test-image',
  type: 'docker',
};

const inheritedParameter: IParameter = {
  default: '',
  description: 'an inherited test parameter',
  hasOptions: false,
  inherited: true,
  label: 'foo',
  name: 'foo',
  options: [{ value: '' }],
  pinned: false,
  required: true,
};

const pipelineParameter: IParameter = {
  default: '',
  description: 'a test parameter',
  hasOptions: false,
  label: 'bar',
  name: 'bar',
  options: [{ value: '' }],
  pinned: false,
  required: true,
};

const inheritedNotification: INotification = {
  address: 'inherited@example.com',
  level: 'pipeline',
  type: 'email',
  when: ['pipeline.starting'],
  inherited: true,
};

const pipelineNotification: INotification = {
  address: 'example@example.com',
  level: 'pipeline',
  type: 'email',
  when: ['pipeline.complete'],
};

const pipeline: IPipelineTemplateConfigV2 = {
  schema: 'v2',
  application: 'app',
  name: 'Test pipeline',
  template: {
    artifactAccount: 'front50ArtifactCredentials',
    reference: 'spinnaker://test-template',
    type: 'front50/pipelineTemplate',
  },
  variables: {},
  exclude: [],
  triggers: [],
  parameterConfig: [],
  notifications: [],
  description: '',
  stages: [],
  expectedArtifacts: [],
  keepWaitingPipelines: false,
  limitConcurrent: true,
  type: 'templatedPipeline',
  updateTs: 1568324929257,
  id: '1234',
};

const plan: IPipelineTemplatePlanV2 = {
  appConfig: {},
  application: 'app',
  expectedArtifacts: [],
  id: '1234',
  keepWaitingPipelines: false,
  lastModifiedBy: 'anonymous',
  limitConcurrent: true,
  name: 'Test pipeline',
  notifications: [inheritedNotification],
  parameterConfig: [inheritedParameter],
  stages: [],
  templateVariables: {},
  triggers: [inheritedDockerTrigger],
  updateTs: 1562959880351,
};

declare const window: IWindowService;

describe('Controller: pipelineConfigurer', function () {
  let $scope: IScope;
  let vm: IController;
  let $q: IQService;

  beforeEach(window.module(require('./pipelineConfigurer').name));

  beforeEach(
    window.inject(function ($controller: IControllerService, $rootScope: IRootScopeService, _$q_: IQService) {
      $q = _$q_;

      $scope = $rootScope.$new();
      $scope.pipeline = pipeline;
      $scope.application = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'pipelineConfigs',
        lazy: true,
        defaultData: [],
      });
      $scope.plan = plan;
      $scope.isTemplatedPipeline = true;
      $scope.isV2TemplatedPipeline = true;

      this.initialize = () => {
        vm = $controller('PipelineConfigurerCtrl', {
          $scope: $scope,
          $uibModal: {},
          $state: {},
          executionService: {},
        });
      };
    }),
  );

  describe('initialization', function () {
    it('sets $scope.renderablePipeline to the plan for templated pipelines', function () {
      this.initialize();
      expect($scope.renderablePipeline).toEqual(plan);
    });
  });

  describe('adding configuration options', function () {
    beforeEach(function () {
      spyOn(PipelineConfigService, 'getHistory').and.returnValue($q.when([]));
      this.initialize();
    });

    it('can add and remove a trigger to the pipeline config and plan', function () {
      expect($scope.pipeline.triggers.length).toBe(0);
      vm.updatePipelineConfig({ triggers: [inheritedDockerTrigger, githubTrigger] });

      $scope.$apply();

      expect($scope.pipeline.triggers.length).toBe(1);
      expect($scope.pipeline.triggers).toEqual([githubTrigger]);
      expect($scope.renderablePipeline.triggers).toEqual([inheritedDockerTrigger, githubTrigger]);

      vm.updatePipelineConfig({ triggers: [inheritedDockerTrigger] });

      $scope.$apply();

      expect($scope.pipeline.triggers.length).toBe(0);
      expect($scope.renderablePipeline.triggers).toEqual([inheritedDockerTrigger]);
    });

    it('can add and remove a parameter to the pipeline config and plan', function () {
      expect($scope.pipeline.parameterConfig.length).toBe(0);
      vm.updatePipelineConfig({ parameterConfig: [inheritedParameter, pipelineParameter] });

      $scope.$apply();

      expect($scope.pipeline.parameterConfig.length).toBe(1);
      expect($scope.pipeline.parameterConfig).toEqual([pipelineParameter]);
      expect($scope.renderablePipeline.parameterConfig).toEqual([inheritedParameter, pipelineParameter]);

      vm.updatePipelineConfig({ parameterConfig: [inheritedParameter] });

      $scope.$apply();

      expect($scope.pipeline.parameterConfig.length).toBe(0);
      expect($scope.renderablePipeline.parameterConfig).toEqual([inheritedParameter]);
    });

    it('can add and remove a notification to the pipeline config and plan', function () {
      expect($scope.pipeline.notifications.length).toBe(0);
      vm.updatePipelineConfig({ notifications: [inheritedNotification, pipelineNotification] });

      $scope.$apply();

      expect($scope.pipeline.notifications.length).toBe(1);
      expect($scope.pipeline.notifications).toEqual([pipelineNotification]);
      expect($scope.renderablePipeline.notifications).toEqual([inheritedNotification, pipelineNotification]);

      vm.updatePipelineConfig({ notifications: [inheritedNotification] });

      $scope.$apply();

      expect($scope.pipeline.notifications.length).toBe(0);
      expect($scope.renderablePipeline.notifications).toEqual([inheritedNotification]);
    });
  });
});
