import { IComponentControllerService, mock } from 'angular';
import {
  DeployInitializerController,
  DEPLOY_INITIALIZER_COMPONENT,
} from 'core/serverGroup/configure/common/deployInitializer.component';
import { Application, ApplicationModelBuilder, APPLICATION_MODEL_BUILDER } from 'core/application';

describe('Component: deployInitializer', () => {
  let ctrl: DeployInitializerController,
    $componentController: IComponentControllerService,
    applicationModelBuilder: ApplicationModelBuilder,
    application: Application;

  const initialize = () => {
    ctrl = $componentController(
      'deployInitializer',
      {},
      { application, command: { viewState: {} }, cloudProvider: 'aws' },
    ) as DeployInitializerController;
    ctrl.$onInit();
  };

  beforeEach(mock.module(DEPLOY_INITIALIZER_COMPONENT, APPLICATION_MODEL_BUILDER));

  beforeEach(
    mock.inject(
      (_applicationModelBuilder_: ApplicationModelBuilder, _$componentController_: IComponentControllerService) => {
        applicationModelBuilder = _applicationModelBuilder_;
        $componentController = _$componentController_;
      },
    ),
  );

  describe('template initialization', () => {
    it('creates separate template options for each account and region of a cluster', () => {
      application = applicationModelBuilder.createApplicationForTests('app', { key: 'serverGroups', lazy: true });
      application.getDataSource('serverGroups').data = [
        {
          name: 'sg1',
          cluster: 'cluster1',
          account: 'test',
          region: 'us-east-1',
          cloudProvider: 'aws',
          category: 'serverGroup',
        },
        {
          name: 'sg2',
          cluster: 'cluster1',
          account: 'prod',
          region: 'us-east-1',
          cloudProvider: 'aws',
          category: 'serverGroup',
        },
        {
          name: 'sg2',
          cluster: 'cluster1',
          account: 'prod',
          region: 'us-east-1',
          cloudProvider: 'aws',
          category: 'serverGroup',
        },
      ];

      initialize();
      const templates = ctrl.templates;

      expect(templates.length).toBe(3);

      // first template is always "None"
      expect(templates[0].label).toBe('None');
      expect(templates[1].cluster).toBe('cluster1');
      expect(templates[1].cluster).toBe('cluster1');
      expect(templates[2].cluster).toBe('cluster1');
    });
  });
});
