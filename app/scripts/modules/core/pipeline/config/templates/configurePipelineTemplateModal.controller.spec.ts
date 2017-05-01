import {mock, IScope, IQService} from 'angular';

import {CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL, ConfigurePipelineTemplateModalController} from './configurePipelineTemplateModal.controller';
import {PIPELINE_TEMPLATE_SERVICE, pipelineTemplateService} from './pipelineTemplate.service';
import {IVariableError} from './variableInput.service';
import {APPLICATION_MODEL_BUILDER, ApplicationModelBuilder} from 'core/application/applicationModel.builder';
import {Application} from 'core/application/application.model';

describe('Controller: ConfigurePipelineTemplateModalCtrl', () => {
  let ctrl: ConfigurePipelineTemplateModalController,
    $scope: IScope,
    $q: IQService,
    application: Application;

  const yaml =
`- name: orca-test
  region: us-west-2
  availabilityZones: []
  capacity: 1
  keyPair: example-keypair
  loadBalancers:
  - orca-test
  securityGroups: []
  strategy: highlander
  instanceType: m3.xlarge`;

  beforeEach(
    mock.module(
      APPLICATION_MODEL_BUILDER,
      CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL,
      PIPELINE_TEMPLATE_SERVICE
    )
  );

  beforeEach(() => {
    mock.inject(($controller: ng.IControllerService, $rootScope: ng.IRootScopeService, _$q_: IQService, applicationModelBuilder: ApplicationModelBuilder) => {
      application = applicationModelBuilder.createStandaloneApplication('app');
      $scope = $rootScope.$new();
      $q = _$q_;
      ctrl = $controller('ConfigurePipelineTemplateModalCtrl', {
        $scope,
        application,
        $uibModalInstance: {close: $q.resolve(null)},
        source: 'https://templateSource.com',
        pipelineName: 'My DCD Pipeline',
        variables: null,
      }) as ConfigurePipelineTemplateModalController;
    });
  });

  beforeEach(() => {
    spyOn(pipelineTemplateService, 'getPipelineTemplateFromSourceUrl').and.returnValue($q.resolve({
      variables: [
        {
          name: 'credentials',
          group: 'Basic Settings',
          type: 'string',
          defaultValue: 'my-google-account',
        },
        {
          name: 'cloudProvider',
          group: 'Basic Settings',
          type: 'string',
          defaultValue: 'gce',
        },
        {
          name: 'someObject',
          group: 'Advanced Settings',
          type: 'object',
          defaultValue: yaml,
        },
        {
          name: 'someList',
          group: 'Advanced Settings',
          type: 'list',
          defaultValue: ['a', 'b', 'c'],
        },
        {
          name: 'someInt',
          type: 'int',
          defaultValue: 42,
        }
      ]
    }));
  });

  describe('data initialization', () => {
    it('sets `variableMetadataGroups` on the controller, groups by variable metadata `groupName`', () => {
      ctrl.initialize();
      $scope.$digest();

      expect(ctrl.variableMetadataGroups).toBeDefined();
      expect(ctrl.variableMetadataGroups.length).toEqual(3);
      expect(ctrl.variableMetadataGroups.find(g => g.name === 'Basic Settings').variableMetadata.length).toEqual(2);
    });

    it('sets `variables` on the controller, each with a property `value` provided by variable metadata `defaultValue`', () => {
      ctrl.initialize();
      $scope.$digest();

      expect(ctrl.variables).toBeDefined();
      expect(ctrl.variables.length).toEqual(5);
      expect(ctrl.variables.map(v => v.value)).toEqual(['my-google-account', 'gce', yaml, ['a', 'b', 'c'], 42]);
    });

    it('does not re-initialize `variables` if the property already exists on the controller', () => {
      const variables = [{name: 'variable', value: 'alreadyExists', type: 'string', errors: [] as IVariableError[]}];
      ctrl.variables = variables;
      ctrl.initialize();
      $scope.$digest();

      expect(ctrl.variables).toEqual(variables);
    });
  });

  describe('config creation', () => {
    it('builds map of variables', () => {
      ctrl.initialize();
      $scope.$digest();
      const templateConfig = ctrl.buildConfig();

      expect(templateConfig.config.pipeline.variables).toEqual({
        credentials: 'my-google-account',
        cloudProvider: 'gce',
        someObject: [{
          name: 'orca-test',
          region: 'us-west-2',
          availabilityZones: [],
          capacity: 1,
          keyPair: 'example-keypair',
          loadBalancers: ['orca-test'],
          securityGroups: [],
          strategy: 'highlander',
          instanceType: 'm3.xlarge'
        }],
        someList: ['a', 'b', 'c'],
        someInt: 42
      });
    });
  });
});
