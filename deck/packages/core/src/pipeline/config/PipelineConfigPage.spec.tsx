import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { mount } from 'enzyme';
import { cloneDeep } from 'lodash';
import React from 'react';
import { act } from 'react-dom/test-utils';

import { AccountService } from '../../account/AccountService';
import type { IAccountDetails } from '../../account/AccountService';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { ApplicationReader } from '../../application/service/ApplicationReader';
import type { DeckRuntime } from '../../bootstrap/DeckRuntime';
import { createDeckRuntime } from '../../bootstrap/DeckRuntime';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { ViewStateCache } from '../../cache';
import { SETTINGS } from '../../config';
import { PageNavigator, ReactSelectInput } from '../../presentation';
import { ReactModal } from '../../presentation/ReactModal';
import { Registry } from '../../registry';
import { PipelineConfigActions } from './actions/PipelineConfigActions';
import {
  applyStageConfigDefaults,
  COMMON_STAGE_FIELDS,
  PipelineConfigPageComponent,
  STAGE_IDENTITY_FIELDS,
} from './PipelineConfigPage';
import { PipelineGraph } from './graph/PipelineGraph';
import { PipelineConfigService } from './services/PipelineConfigService';
import { StageConfigWrapper } from './stages/StageConfigWrapper';
import { BaseProviderStageConfig } from './stages/baseProviderStage/BaseProviderStageConfig';
import { StageConfigField } from './stages/common/stageConfigField/StageConfigField';
import type { IPipeline, IStageTypeConfig } from '../../domain';
import { ConfigurePipelineTemplateModal } from './templates/ConfigurePipelineTemplateModal';
import { PipelineTemplateReader } from './templates/PipelineTemplateReader';

describe('PipelineConfigPage', () => {
  let $stateParams: { executionId?: string; new?: string; pipelineId?: string };
  let fiatEnabled: boolean;
  let providerRenderStates: boolean[];
  let router: UIRouterReact;
  let runtime: DeckRuntime;
  let stateGo: jasmine.Spy;
  let transitionCleanup: jasmine.Spy;
  let transitionOnBefore: jasmine.Spy;

  const PipelineConfigPage = (props: any) => (
    <UIRouterContext.Provider value={router}>
      <DeckRuntimeContext.Provider value={runtime}>
        <PipelineConfigPageComponent
          {...props}
          router={router}
          stateParams={$stateParams}
          stateService={{ go: stateGo } as any}
        />
      </DeckRuntimeContext.Provider>
    </UIRouterContext.Provider>
  );

  const AwsStageConfig = () => <div className="aws-stage-config">AWS stage config</div>;
  const EcsStageConfig = ({ stage }: { stage: any }) => {
    providerRenderStates.push(stage.cloudProvider === 'ecs' && stage.cloudProviderType === 'ecs');
    return <div className="ecs-stage-config">ECS stage config</div>;
  };
  const RegularStageConfig = () => <div className="regular-stage-config">Regular stage config</div>;

  const account = (cloudProvider: string): IAccountDetails =>
    ({
      accountId: `${cloudProvider}-account-id`,
      accountType: cloudProvider,
      authorized: true,
      challengeDestructiveActions: false,
      cloudProvider,
      environment: 'test',
      name: `${cloudProvider}-account`,
      primaryAccount: false,
      regions: [],
      requiredGroupMembership: [],
      type: cloudProvider,
    } as IAccountDetails);

  const pipeline = (id: string, name: string): IPipeline => ({
    application: 'app',
    id,
    name,
    stages: [],
    triggers: [],
    parameterConfig: [],
    notifications: [],
    limitConcurrent: true,
    keepWaitingPipelines: false,
  });

  const createApp = (pipelines: IPipeline[], strategies: IPipeline[] = []) => {
    const app = ApplicationModelBuilder.createApplicationForTests(
      'app',
      { key: 'pipelineConfigs', lazy: true, defaultData: [] as IPipeline[] },
      { key: 'strategyConfigs', lazy: true, defaultData: [] as IPipeline[] },
    );
    app.pipelineConfigs.data = pipelines;
    app.strategyConfigs.data = strategies;
    spyOn(app.pipelineConfigs, 'activate').and.callThrough();
    spyOn(app.pipelineConfigs, 'refresh').and.returnValue(Promise.resolve(pipelines) as any);
    spyOn(app.strategyConfigs, 'activate').and.callThrough();
    spyOn(app.strategyConfigs, 'refresh').and.returnValue(Promise.resolve(strategies) as any);
    return app;
  };

  const flush = async () => {
    await Promise.resolve();
    await new Promise((resolve) => setTimeout(resolve, 0));
    await Promise.resolve();
  };

  const deferred = <T,>() => {
    let resolve: (value: T) => void;
    let reject: (reason?: any) => void;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
      resolve = resolvePromise;
      reject = rejectPromise;
    });
    return { promise, resolve, reject };
  };

  const templatedV1 = (isNew = false, source = 'spinnaker://template-id') => {
    const config = pipeline('template-pipeline-id', 'Template Pipeline') as any;
    config.type = 'templatedPipeline';
    config.isNew = isNew || undefined;
    config.config = {
      schema: '1',
      pipeline: {
        application: 'app',
        name: config.name,
        pipelineConfigId: config.id,
        template: { source },
        variables: {},
      },
    };
    return config as IPipeline;
  };

  const templatedV2 = (isNew = false) => {
    const config = pipeline('template-pipeline-id', 'Template Pipeline') as any;
    config.type = 'templatedPipeline';
    config.isNew = isNew || undefined;
    config.schema = 'v2';
    config.template = {
      artifactAccount: 'front50ArtifactCredentials',
      reference: 'spinnaker://template-id',
      type: 'front50/pipelineTemplate',
    };
    config.variables = {};
    return config as IPipeline;
  };

  const showStageConfig = (pipelineId: string, stageIndex = 0) => {
    ViewStateCache.get('pipelineConfig').put(`app:${pipelineId}`, { section: 'stage', stageIndex });
  };

  const registerStageTypes = () => {
    Registry.pipeline.registerStage({
      key: 'wait',
      label: 'Wait',
    } as IStageTypeConfig);
    Registry.pipeline.registerStage({
      key: 'manualJudgment',
      label: 'Manual Judgment',
    } as IStageTypeConfig);
  };

  const registerBaseProviderStages = (ecsKey = 'destroyServerGroup') => {
    Registry.pipeline.registerStage({
      key: 'destroyServerGroup',
      label: 'Destroy Server Group',
      useBaseProvider: true,
    } as IStageTypeConfig);
    Registry.pipeline.registerStage({
      key: 'destroyServerGroup',
      provides: 'destroyServerGroup',
      cloudProvider: 'aws',
      component: AwsStageConfig,
    } as IStageTypeConfig);
    Registry.pipeline.registerStage({
      key: ecsKey,
      provides: 'destroyServerGroup',
      providesFor: ['ecs'],
      component: EcsStageConfig,
    } as IStageTypeConfig);
  };

  const commonStageFields = {
    comments: 'Keep this comment',
    notifications: [{ type: 'email', address: 'team@example.com', level: 'stage' }],
    sendNotifications: true,
    failPipeline: true,
    continuePipeline: false,
    completeOtherBranchesThenFail: true,
    failOnFailedExpressions: true,
    stageEnabled: { type: 'expression', expression: '${ parameters.deploy }' },
    restrictExecutionDuringTimeWindow: true,
    restrictedExecutionWindow: { days: [1], startHour: 9, startMin: 30, endHour: 17, endMin: 0 },
    skipWindowText: true,
    stageTimeoutMs: 900000,
    expectedArtifacts: [{ id: 'artifact-id', displayName: 'manifest', matchArtifact: { type: 'kubernetes/manifest' } }],
  };

  const providerStage = (provider: 'aws' | 'ecs') =>
    ({
      refId: '2',
      requisiteStageRefIds: ['1'],
      isNew: true,
      name: 'Custom destroy name',
      type: 'destroyServerGroup',
      cloudProvider: provider,
      cloudProviderType: provider,
      credentials: `${provider}-account`,
      regions: [`${provider}-region`],
      cluster: `${provider}-cluster`,
      target: `${provider}-target`,
      account: `${provider}-account`,
      region: `${provider}-region`,
      availabilityZones: { [`${provider}-region`]: ['zone-a'] },
      capacity: { min: 1, max: 2, desired: 1 },
      source: { account: `${provider}-source` },
      [`${provider}ProviderField`]: `${provider}-specific`,
      ...cloneDeep(commonStageFields),
    } as any);

  const expectCommonFieldsPreserved = (stage: any) => {
    expect(stage).toEqual(
      jasmine.objectContaining({
        refId: '2',
        requisiteStageRefIds: ['1'],
        isNew: true,
        name: 'Custom destroy name',
        ...commonStageFields,
      }),
    );
  };

  const expectProviderFieldsRemoved = (stage: any, previousProvider: 'aws' | 'ecs') => {
    [
      'credentials',
      'regions',
      'cluster',
      'target',
      'account',
      'region',
      'availabilityZones',
      'capacity',
      'source',
      `${previousProvider}ProviderField`,
    ].forEach((field) => expect(stage[field]).toBeUndefined());
  };

  it('defines distinct minimal retention policies for stage type and provider changes', () => {
    expect(STAGE_IDENTITY_FIELDS).toEqual(['requisiteStageRefIds', 'refId', 'isNew', 'name', 'type']);
    expect(COMMON_STAGE_FIELDS).toEqual([
      ...STAGE_IDENTITY_FIELDS,
      'comments',
      'notifications',
      'sendNotifications',
      'failPipeline',
      'continuePipeline',
      'completeOtherBranchesThenFail',
      'failOnFailedExpressions',
      'stageEnabled',
      'restrictExecutionDuringTimeWindow',
      'restrictedExecutionWindow',
      'skipWindowText',
      'stageTimeoutMs',
      'expectedArtifacts',
    ]);
  });

  it('reports serialized changes and clones defaults when applying stage configuration', () => {
    const defaults = { nested: { value: 'default value' } };
    const stage = { refId: '1', name: '', type: 'regular', requisiteStageRefIds: [] } as any;
    const config = {
      key: 'regular',
      label: 'Regular',
      alias: 'legacyRegular',
      addAliasToConfig: true,
      defaults,
    } as IStageTypeConfig;

    expect(applyStageConfigDefaults(stage, config)).toBe(true);
    expect(stage).toEqual(
      jasmine.objectContaining({ name: 'Regular', alias: 'legacyRegular', nested: { value: 'default value' } }),
    );
    expect(stage.nested).not.toBe(defaults.nested);
    expect(defaults).toEqual({ nested: { value: 'default value' } });
    expect(applyStageConfigDefaults(stage, config)).toBe(false);
  });

  beforeEach(() => {
    $stateParams = {};
    router = new UIRouterReact();
    runtime = createDeckRuntime(router);
    stateGo = jasmine.createSpy('stateGo');
    transitionCleanup = jasmine.createSpy('transitionCleanup');
    transitionOnBefore = spyOn(router.transitionService, 'onBefore').and.returnValue(transitionCleanup);
    fiatEnabled = SETTINGS.feature.fiatEnabled;
    providerRenderStates = [];
    Registry.reinitialize();
    ViewStateCache.get('pipelineConfig').removeAll();
    spyOn(AccountService, 'applicationAccounts').and.callFake(() => Promise.resolve([account('aws')]) as any);
    spyOn(ApplicationReader, 'getApplicationPermissions').and.returnValue(Promise.resolve({}) as any);
    spyOn(runtime.services.executionService, 'getExecutionsForConfigIds').and.returnValue(Promise.resolve([]));
  });

  afterEach(() => {
    router.dispose();
    runtime.dispose();
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled };
    Registry.reinitialize();
    ViewStateCache.get('pipelineConfig').removeAll();
  });

  it('refreshes configs and renders the requested pipeline configurer by id', async () => {
    const requested = pipeline('target-id', 'Requested Pipeline');
    const app = createApp([pipeline('first-id', 'First Pipeline'), requested]);
    $stateParams.pipelineId = requested.id;

    const wrapper = mount(<PipelineConfigPage app={app} className="flex-fill" />);
    await flush();
    wrapper.update();

    expect(app.pipelineConfigs.activate).toHaveBeenCalled();
    expect(app.pipelineConfigs.refresh).toHaveBeenCalled();
    expect(wrapper.find('.pipeline-configurer').exists()).toBe(true);
    expect(wrapper.find('.pipeline-config-heading h3').text()).toContain('Requested Pipeline');

    wrapper.unmount();
  });

  it('uses the injected router for transition guarding and back navigation', async () => {
    const requested = pipeline('target-id', 'Requested Pipeline');
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    wrapper.find('.btn-configure').simulate('click');
    expect(stateGo).toHaveBeenCalledWith('^.executions');
    expect(transitionOnBefore).toHaveBeenCalledWith({}, jasmine.any(Function));

    wrapper.unmount();
    expect(transitionCleanup).toHaveBeenCalled();
  });

  it('renders the React pipeline config layout wrappers', async () => {
    const app = createApp([pipeline('target-id', 'Requested Pipeline')]);
    $stateParams.pipelineId = 'target-id';

    const wrapper = mount(<PipelineConfigPage app={app} className="flex-fill" />);
    await flush();
    wrapper.update();

    expect(wrapper.find('pipeline-configurer').exists()).toBe(false);
    expect(wrapper.find('.pipeline-config-page.container-fluid').exists()).toBe(true);
    expect(wrapper.find('.pipeline-config-page.full-width').exists()).toBe(true);
    expect(wrapper.find('.pipeline-config-page.flex-fill').exists()).toBe(true);
    expect(wrapper.find('.pipeline-configurer').exists()).toBe(true);
    expect(wrapper.find('.pipeline-configurer').closest('.col-md-10.col-md-offset-1').exists()).toBe(true);
    expect(wrapper.find('.pipeline-config-view .row.horizontal > .col-md-12').exists()).toBe(true);

    wrapper.unmount();
  });

  it('uses the pipeline config page as the scroll container for page navigation', async () => {
    const app = createApp([pipeline('target-id', 'Requested Pipeline')]);
    $stateParams.pipelineId = 'target-id';

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    expect(wrapper.find(PageNavigator).prop('scrollableContainer')).toBe('.pipeline-config-page');

    wrapper.unmount();
  });

  it('uses the custom stage type selector when adding a stage', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: '', type: '', isNew: true, requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const typeSelect = wrapper.find(ReactSelectInput).filterWhere((node) => node.prop('name') === 'type');
    expect(typeSelect.exists()).toBe(true);
    expect(typeSelect.prop('inputClassName')).not.toContain('input-sm');
    expect((typeSelect.prop('options') as any[]).map((option) => option.label)).toEqual(['Manual Judgment', 'Wait']);
    expect(wrapper.find('.pipeline-stage-config-heading select').exists()).toBe(false);

    const renderedOption = mount(
      (typeSelect.prop('optionRenderer') as any)({
        label: 'Wait',
        value: 'wait',
        description: 'Pauses execution before continuing.',
      }),
    );
    expect(renderedOption.find('.stage-choice').exists()).toBe(true);
    expect(renderedOption.text()).toContain('Pauses execution before continuing.');

    renderedOption.unmount();
    wrapper.unmount();
  });

  it('keeps focus on a stage field after the new-stage type selector initially autofocuses', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Build', type: 'wait', isNew: true, requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    const host = document.createElement('div');
    document.body.appendChild(host);
    const wrapper = mount(<PipelineConfigPage app={app} />, { attachTo: host });

    try {
      await flush();
      wrapper.update();

      const typeInput = host.querySelector('.pipeline-stage-type-select input[role="combobox"]') as HTMLInputElement;
      const stageNameInput = wrapper
        .find('.pipeline-stage-config-heading input[type="text"]')
        .filterWhere((node) => node.prop('value') === 'Build');
      const stageNameElement = stageNameInput.getDOMNode() as HTMLInputElement;

      expect(document.activeElement).toBe(typeInput);

      stageNameElement.focus();
      expect(document.activeElement).toBe(stageNameElement);

      await act(async () => {
        stageNameInput.prop('onChange')({ target: { value: 'Updated Build' } } as any);
        await flush();
      });
      wrapper.update();

      expect(document.activeElement).toBe(stageNameElement);
    } finally {
      wrapper.unmount();
      host.remove();
    }
  });

  it('uses the custom multi selector for stage dependencies', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any,
      { refId: '2', name: 'Deploy', type: 'wait', requisiteStageRefIds: ['1'] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const dependencySelect = wrapper
      .find(ReactSelectInput)
      .filterWhere((node) => node.prop('name') === 'requisiteStageRefIds');
    expect(dependencySelect.exists()).toBe(true);
    expect(dependencySelect.prop('multi')).toBe(true);
    expect(dependencySelect.prop('inputClassName')).toContain('pipeline-stage-dependency-select');
    expect(dependencySelect.prop('inputClassName')).not.toContain('input-sm');
    expect(dependencySelect.prop('options')).toContain(jasmine.objectContaining({ label: 'Build', value: '1' }));
    expect(wrapper.find('.pipeline-stage-config-heading select[multiple]').exists()).toBe(false);

    wrapper.unmount();
  });

  it('keeps the stage header labels close to their controls', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any,
      { refId: '2', name: 'Deploy', type: 'wait', requisiteStageRefIds: ['1'] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const headerFields = wrapper
      .find(StageConfigField)
      .filterWhere((node) => ['Stage Name', 'Depends On'].includes(node.prop('label')));

    expect(headerFields.length).toBe(2);
    headerFields.forEach((field) => {
      expect(field.prop('labelColumns')).toBe(2);
      expect(field.prop('fieldColumns')).toBe(9);
    });

    wrapper.unmount();
  });

  it('replaces the graph pipeline when stage dependencies change', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any,
      { refId: '2', name: 'Deploy', type: 'wait', requisiteStageRefIds: [] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const originalGraphPipeline = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
    const dependencySelect = wrapper
      .find(ReactSelectInput)
      .filterWhere((node) => node.prop('name') === 'requisiteStageRefIds');

    await act(async () => {
      dependencySelect.prop('onChange')({ target: { value: ['1'] } } as any);
      await flush();
    });
    wrapper.update();

    const updatedGraphPipeline = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
    expect(updatedGraphPipeline).not.toBe(originalGraphPipeline);
    expect(updatedGraphPipeline.stages[1].requisiteStageRefIds).toEqual(['1']);

    wrapper.unmount();
  });

  it('replaces the graph pipeline when stage fields change', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const originalGraphPipeline = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
    const stageNameInput = wrapper
      .find('.pipeline-stage-config-heading input[type="text"]')
      .filterWhere((node) => node.prop('value') === 'Build');

    await act(async () => {
      stageNameInput.prop('onChange')({ target: { value: 'Bake' } } as any);
      await flush();
    });
    wrapper.update();

    const updatedGraphPipeline = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
    expect(updatedGraphPipeline).not.toBe(originalGraphPipeline);
    expect(updatedGraphPipeline.stages[0].name).toBe('Bake');

    wrapper.unmount();
  });

  it('renders direct React common execution controls for stages', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      {
        refId: '1',
        name: 'Build',
        type: 'wait',
        requisiteStageRefIds: [],
        failOnFailedExpressions: false,
      } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    await act(async () => {
      wrapper.find('[data-test-id="fail-on-failed-expressions"]').first().prop('onChange')({
        target: { checked: true },
      } as any);
      wrapper.find('[data-test-id="optional-stage-enabled"]').first().prop('onChange')({
        target: { checked: true },
      } as any);
      await flush();
    });
    wrapper.update();

    await act(async () => {
      wrapper.find('[data-test-id="optional-stage-expression"]').first().prop('onChange')({
        target: { value: '${ parameters.deploy }' },
      } as any);
      await flush();
    });
    wrapper.update();

    const updatedPipeline = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
    expect(updatedPipeline.stages[0].failOnFailedExpressions).toBe(true);
    expect(updatedPipeline.stages[0].stageEnabled).toEqual({
      type: 'expression',
      expression: '${ parameters.deploy }',
    });

    wrapper.unmount();
  });

  it('preserves existing stage notifications when generic notification sending is disabled', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      {
        refId: '1',
        name: 'Build',
        type: 'wait',
        requisiteStageRefIds: [],
        sendNotifications: true,
        notifications: [{ type: 'email', address: 'team@example.com' }],
      } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const notificationsToggle = wrapper
      .find('input[type="checkbox"]')
      .filterWhere((node) => node.prop('checked') === true);
    await act(async () => {
      notificationsToggle.last().prop('onChange')({ target: { checked: false } } as any);
      await flush();
    });
    wrapper.update();

    const updatedPipeline = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
    expect(updatedPipeline.stages[0].sendNotifications).toBeUndefined();
    expect(updatedPipeline.stages[0].notifications).toEqual([{ type: 'email', address: 'team@example.com' }]);

    wrapper.unmount();
  });

  it('renders manual judgment authorized groups from application permissions', async () => {
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    (ApplicationReader.getApplicationPermissions as jasmine.Spy).and.returnValue(
      Promise.resolve({ READ: ['readers'], WRITE: ['writers'], EXECUTE: ['executors'] }) as any,
    );
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      {
        refId: '1',
        name: 'Judge',
        type: 'manualJudgment',
        requisiteStageRefIds: [],
        selectedStageRoles: ['writers'],
      } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const authorizedGroupsSelect = wrapper
      .find(ReactSelectInput)
      .filterWhere((node) => node.prop('name') === 'selectedStageRoles');
    expect(authorizedGroupsSelect.exists()).toBe(true);
    expect(authorizedGroupsSelect.prop('multi')).toBe(true);
    expect(authorizedGroupsSelect.prop('options')).toContain(
      jasmine.objectContaining({ label: 'readers', value: 'readers' }),
    );
    expect(authorizedGroupsSelect.prop('options')).toContain(
      jasmine.objectContaining({ label: 'writers', value: 'writers' }),
    );
    expect(authorizedGroupsSelect.prop('options')).toContain(
      jasmine.objectContaining({ label: 'executors', value: 'executors' }),
    );

    await act(async () => {
      authorizedGroupsSelect.prop('onChange')({ target: { value: ['executors'] } } as any);
      await flush();
    });
    wrapper.update();

    const updatedPipeline = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
    expect(updatedPipeline.stages[0].selectedStageRoles).toEqual(['executors']);

    wrapper.unmount();
  });

  it('shows template configuration without Angular modal support', async () => {
    const requested = templatedV1();
    const plan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValue(Promise.resolve(plan));

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const configureTemplateButtons = wrapper
      .find('button')
      .filterWhere((node) => node.text().includes('Configure Template'));
    expect(configureTemplateButtons.exists()).toBe(true);

    wrapper.unmount();
  });

  it('opens the shared React modal with a clone and makes successful configuration revertible', async () => {
    const requested = templatedV1();
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    (originalPlan as any).executionId = 'rendered-execution-id';
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    $stateParams.executionId = 'route-execution-id';
    spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValue(Promise.resolve(originalPlan));
    const modalResult = deferred<{ plan: IPipeline; config: IPipeline }>();
    const showModal = spyOn(ReactModal, 'show').and.returnValue(modalResult.promise);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const configure = wrapper.find('[data-test-id="configure-template"]');
    configure.prop('onClick')();
    const modalProps = showModal.calls.mostRecent().args[1] as any;
    expect(showModal.calls.mostRecent().args[0]).toBe(ConfigurePipelineTemplateModal);
    expect(modalProps).toEqual(
      jasmine.objectContaining({
        application: app,
        executionId: 'rendered-execution-id',
        isNew: requested.isNew,
        pipelineId: requested.id,
      }),
    );
    expect(modalProps.pipelineTemplateConfig).toEqual(requested);
    expect(modalProps.pipelineTemplateConfig).not.toBe(requested);
    modalProps.pipelineTemplateConfig.name = 'mutated clone';
    expect((wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).name).toBe('Template Pipeline');

    const configured = { ...requested, isNew: true, name: 'Configured Pipeline' } as IPipeline;
    const configuredPlan = { ...originalPlan, name: 'Configured Pipeline' } as IPipeline;
    await act(async () => {
      modalResult.resolve({ plan: configuredPlan, config: configured });
      await flush();
    });
    wrapper.update();

    expect((wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).name).toBe('Configured Pipeline');
    expect(wrapper.text()).toContain('Save Changes');
    expect(wrapper.find('[data-test-id="Pipeline.revertChanges"]').exists()).toBe(true);

    await act(async () => {
      wrapper.find('[data-test-id="Pipeline.revertChanges"]').prop('onClick')();
      await flush();
    });
    wrapper.update();

    expect((wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).name).toBe('Template Pipeline');
    expect(wrapper.find('[data-test-id="Pipeline.revertChanges"]').exists()).toBe(false);

    wrapper.unmount();
  });

  it('applies a template modal result after execution enrichment replaces the same pipeline model', async () => {
    const requested = templatedV1(false, 'https://templates.example/{{ execution.id }}');
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    (originalPlan as any).executionId = 'rendered-execution-id';
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValue(Promise.resolve(originalPlan));
    const executionEnrichment = deferred<any[]>();
    (runtime.services.executionService.getExecutionsForConfigIds as jasmine.Spy).and.returnValue(
      executionEnrichment.promise,
    );
    const modalResult = deferred<{ plan: IPipeline; config: IPipeline }>();
    spyOn(ReactModal, 'show').and.returnValue(modalResult.promise);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();
    wrapper.find('[data-test-id="configure-template"]').prop('onClick')();

    await act(async () => {
      executionEnrichment.resolve([
        { id: 'rendered-execution-id', name: 'Enriched execution', stages: [], trigger: {} },
      ]);
      await flush();
    });
    wrapper.update();

    expect(wrapper.text()).toContain('Enriched execution');
    expect(wrapper.find('[data-test-id="configure-template"]').prop('disabled')).toBe(true);

    const configured = { ...requested, name: 'Configured Pipeline' } as IPipeline;
    const configuredPlan = { ...pipeline(requested.id, 'Configured Pipeline'), stages: [] } as IPipeline;
    await act(async () => {
      modalResult.resolve({ plan: configuredPlan, config: configured });
      await flush();
    });
    wrapper.update();

    const rendered = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
    expect(rendered.name).toBe('Configured Pipeline');
    expect((rendered as any).executionId).toBe('rendered-execution-id');
    expect(wrapper.text()).toContain('Enriched execution');
    expect(wrapper.text()).toContain('Save Changes');
    expect(wrapper.find('[data-test-id="configure-template"]').prop('disabled')).toBe(false);

    wrapper.unmount();
  });

  it('applies only the latest template modal result and keeps loading until it completes', async () => {
    const requested = templatedV1();
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValue(Promise.resolve(originalPlan));
    const firstModal = deferred<{ plan: IPipeline; config: IPipeline }>();
    const secondModal = deferred<{ plan: IPipeline; config: IPipeline }>();
    const showModal = spyOn(ReactModal, 'show').and.returnValues(firstModal.promise, secondModal.promise);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const configure = wrapper.find('[data-test-id="configure-template"]').prop('onClick') as () => void;
    await act(async () => {
      configure();
      configure();
      await flush();
    });
    wrapper.update();

    expect(showModal).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-test-id="configure-template"]').prop('disabled')).toBe(true);

    const firstConfig = { ...requested, name: 'Stale First Config' } as IPipeline;
    const firstPlan = { ...originalPlan, name: 'Stale First Plan' } as IPipeline;
    await act(async () => {
      firstModal.resolve({ plan: firstPlan, config: firstConfig });
      await flush();
    });
    wrapper.update();

    expect((wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).name).toBe(requested.name);
    expect(wrapper.find('[data-test-id="configure-template"]').prop('disabled')).toBe(true);

    const secondConfig = { ...requested, name: 'Latest Config' } as IPipeline;
    const secondPlan = { ...originalPlan, name: 'Latest Plan' } as IPipeline;
    await act(async () => {
      secondModal.resolve({ plan: secondPlan, config: secondConfig });
      await flush();
    });
    wrapper.update();

    expect((wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).name).toBe('Latest Plan');
    expect(wrapper.find('[data-test-id="configure-template"]').prop('disabled')).toBe(false);
    expect(wrapper.text()).toContain('Save Changes');
    wrapper.unmount();
  });

  it('ignores a template modal result after a different pipeline load supersedes it', async () => {
    const requested = templatedV1();
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    const reloaded = { ...templatedV1(), id: 'reloaded-pipeline-id', name: 'Reloaded Pipeline' } as IPipeline;
    (reloaded as any).config.pipeline.name = reloaded.name;
    (reloaded as any).config.pipeline.pipelineConfigId = reloaded.id;
    const reloadedPlan = { ...pipeline(reloaded.id, reloaded.name), stages: [] } as IPipeline;
    const reloadedApp = createApp([reloaded]);
    spyOn(PipelineTemplateReader, 'getPipelinePlan').and.callFake((config: IPipeline) =>
      Promise.resolve(config.name === reloaded.name ? reloadedPlan : originalPlan),
    );
    const modalResult = deferred<{ plan: IPipeline; config: IPipeline }>();
    spyOn(ReactModal, 'show').and.returnValue(modalResult.promise);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();
    wrapper.find('[data-test-id="configure-template"]').prop('onClick')();

    $stateParams.pipelineId = reloaded.id;
    wrapper.setProps({ app: reloadedApp });
    await flush();
    wrapper.update();
    expect((wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).name).toBe(reloaded.name);

    await act(async () => {
      modalResult.resolve({
        plan: { ...originalPlan, name: 'Stale Modal Plan' },
        config: { ...requested, name: 'Stale Modal Config' },
      });
      await flush();
    });
    wrapper.update();

    expect((wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).name).toBe(reloaded.name);
    expect(wrapper.text()).not.toContain('Save Changes');
    expect(wrapper.find('[data-test-id="configure-template"]').prop('disabled')).toBe(false);
    wrapper.unmount();
  });

  it('always marks a successful template configuration dirty and only save establishes the new baseline', async () => {
    const requested = templatedV1();
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValue(Promise.resolve(originalPlan));
    spyOn(PipelineConfigService, 'savePipeline').and.returnValue(Promise.resolve());
    spyOn(ReactModal, 'show').and.returnValue(
      Promise.resolve({ plan: cloneDeep(originalPlan), config: cloneDeep(requested) }),
    );

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    await act(async () => {
      wrapper.find('[data-test-id="configure-template"]').prop('onClick')();
      await flush();
    });
    wrapper.update();
    expect(wrapper.text()).toContain('Save Changes');

    await act(async () => {
      wrapper
        .find('button.btn-primary')
        .filterWhere((button) => button.text().includes('Save Changes'))
        .prop('onClick')();
      await flush();
    });
    wrapper.update();

    expect(PipelineConfigService.savePipeline).toHaveBeenCalledWith(
      jasmine.objectContaining({ id: requested.id, name: requested.name }),
    );
    expect(wrapper.text()).toContain('In sync with server');
    expect(wrapper.find('[data-test-id="Pipeline.revertChanges"]').exists()).toBe(false);

    wrapper.unmount();
  });

  it('auto-opens a new static V1 template exactly once and does not reopen after dismissal', async () => {
    const requested = templatedV1(true);
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    $stateParams.new = '1';
    const modalResult = deferred<any>();
    const showModal = spyOn(ReactModal, 'show').and.returnValue(modalResult.promise);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();
    expect(showModal).toHaveBeenCalledTimes(1);

    await act(async () => {
      modalResult.reject('dismissed');
      await flush();
    });
    wrapper.setProps({ app });
    await flush();
    wrapper.update();

    expect(showModal).toHaveBeenCalledTimes(1);
    expect((wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).name).toBe(requested.name);
    expect(wrapper.find('[data-test-id="configure-template"]').prop('disabled')).toBe(false);

    wrapper.unmount();
  });

  it('auto-opens a new V2 template exactly once', async () => {
    const requested = templatedV2(true);
    const plan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    $stateParams.new = '1';
    spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValue(Promise.resolve(plan));
    const modalResult = deferred<any>();
    const showModal = spyOn(ReactModal, 'show').and.returnValue(modalResult.promise);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();
    expect(showModal).toHaveBeenCalledTimes(1);

    await act(async () => {
      modalResult.reject('dismissed');
      await flush();
    });
    wrapper.setProps({ app });
    await flush();
    expect(showModal).toHaveBeenCalledTimes(1);

    wrapper.unmount();
  });

  it('does not auto-open a new dynamic V1 template', async () => {
    const requested = templatedV1(true, 'https://templates.example/{{ execution.id }}');
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    $stateParams.new = '1';
    const showModal = spyOn(ReactModal, 'show');

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    expect(showModal).not.toHaveBeenCalled();

    wrapper.unmount();
  });

  it('ignores template modal completion after unmount', async () => {
    const requested = templatedV1();
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValue(Promise.resolve(originalPlan));
    const modalResult = deferred<{ plan: IPipeline; config: IPipeline }>();
    spyOn(ReactModal, 'show').and.returnValue(modalResult.promise);
    const consoleError = spyOn(console, 'error');

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();
    wrapper.find('[data-test-id="configure-template"]').prop('onClick')();
    wrapper.unmount();

    await act(async () => {
      modalResult.resolve({ plan: originalPlan, config: requested });
      await flush();
    });

    expect(consoleError).not.toHaveBeenCalled();
  });

  it('saves the selected history revision when restoring pipeline history', async () => {
    const requested = pipeline('target-id', 'Current Pipeline');
    const restored = { ...pipeline('target-id', 'Restored Pipeline'), updateTs: 'old-revision' };
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    spyOn(ReactModal, 'show').and.returnValue(Promise.resolve(restored));
    spyOn(PipelineConfigService, 'savePipeline').and.returnValue(Promise.resolve());

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    await act(async () => {
      wrapper.find(PipelineConfigActions).prop('showHistory')();
      await flush();
    });

    expect(PipelineConfigService.savePipeline).toHaveBeenCalledWith(
      jasmine.objectContaining({
        name: 'Restored Pipeline',
      }),
    );

    wrapper.unmount();
  });

  it('loads accounts once for an app, shows pending state, and ignores completion after unmount', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: '', type: '', isNew: true, requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    let resolveAccounts: (accounts: IAccountDetails[]) => void;
    const accountRequest = new Promise<IAccountDetails[]>((resolve) => (resolveAccounts = resolve));
    (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(accountRequest as any);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    expect(AccountService.applicationAccounts).toHaveBeenCalledOnceWith(app);
    expect(wrapper.text()).toContain('Loading application accounts...');
    expect(
      wrapper
        .find(StageConfigField)
        .filterWhere((node) => node.prop('label') === 'Stage Name')
        .exists(),
    ).toBe(true);
    expect(wrapper.find('[data-test-id="fail-on-failed-expressions"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('Remove stage');
    expect(wrapper.text()).toContain('Edit stage as JSON');
    expect(
      wrapper
        .find(ReactSelectInput)
        .filterWhere((node) => node.prop('name') === 'type')
        .exists(),
    ).toBe(false);

    wrapper.setProps({ app });
    await flush();
    expect(AccountService.applicationAccounts).toHaveBeenCalledTimes(1);

    wrapper.unmount();
    await act(async () => {
      resolveAccounts([account('aws')]);
      await flush();
    });

    expect(AccountService.applicationAccounts).toHaveBeenCalledTimes(1);
  });

  it('renders an account loading error without unfiltered stage type choices', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: '', type: '', isNew: true, requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    (AccountService.applicationAccounts as jasmine.Spy).and.callFake(() =>
      Promise.reject(new Error('accounts failed')),
    );
    const getConfigurableStageTypes = spyOn(Registry.pipeline, 'getConfigurableStageTypes').and.callThrough();

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    expect(wrapper.text()).toContain('Could not load application accounts: accounts failed');
    expect(
      wrapper
        .find(StageConfigField)
        .filterWhere((node) => node.prop('label') === 'Stage Name')
        .exists(),
    ).toBe(true);
    expect(wrapper.find('[data-test-id="fail-on-failed-expressions"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('Remove stage');
    expect(wrapper.text()).toContain('Edit stage as JSON');
    expect(
      wrapper
        .find(ReactSelectInput)
        .filterWhere((node) => node.prop('name') === 'type')
        .exists(),
    ).toBe(false);
    expect(getConfigurableStageTypes).not.toHaveBeenCalled();

    wrapper.unmount();
  });

  it('renders a successful empty account state without unfiltered stage type choices', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: '', type: '', isNew: true, requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(Promise.resolve([]) as any);
    const getConfigurableStageTypes = spyOn(Registry.pipeline, 'getConfigurableStageTypes').and.callThrough();

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    expect(wrapper.text()).toContain('No application accounts are available.');
    expect(
      wrapper
        .find(StageConfigField)
        .filterWhere((node) => node.prop('label') === 'Stage Name')
        .exists(),
    ).toBe(true);
    expect(wrapper.find('[data-test-id="fail-on-failed-expressions"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('Remove stage');
    expect(wrapper.text()).toContain('Edit stage as JSON');
    expect(
      wrapper
        .find(ReactSelectInput)
        .filterWhere((node) => node.prop('name') === 'type')
        .exists(),
    ).toBe(false);
    expect(getConfigurableStageTypes).not.toHaveBeenCalled();

    wrapper.unmount();
  });

  it('filters an unselected base stage to ECS accounts and renders only the ECS implementation', async () => {
    registerBaseProviderStages();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      {
        refId: '1',
        name: 'Destroy Server Group',
        type: 'destroyServerGroup',
        isNew: true,
        requisiteStageRefIds: [],
      } as any,
    ];
    const app = createApp([requested]);
    const accounts = [account('ecs')];
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(Promise.resolve(accounts) as any);
    const getConfigurableStageTypes = spyOn(Registry.pipeline, 'getConfigurableStageTypes').and.callThrough();

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const updatedPipeline = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
    expect(getConfigurableStageTypes).toHaveBeenCalledWith(accounts);
    expect(
      (wrapper
        .find(ReactSelectInput)
        .filterWhere((node) => node.prop('name') === 'type')
        .prop('options') as any[])[0],
    ).toEqual(jasmine.objectContaining({ key: 'destroyServerGroup', cloudProviders: ['ecs'] }));
    expect(updatedPipeline.stages[0]).toEqual(
      jasmine.objectContaining({
        type: 'destroyServerGroup',
        cloudProvider: 'ecs',
        cloudProviderType: 'ecs',
      }),
    );
    expect(wrapper.find('.ecs-stage-config').exists()).toBe(true);
    expect(wrapper.find('.aws-stage-config').exists()).toBe(false);

    wrapper.unmount();
  });

  it('infers a persisted singleton provider without deleting existing stage configuration', async () => {
    registerBaseProviderStages('customDestroyServerGroup');
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    const persistedStage = providerStage('ecs');
    delete persistedStage.isNew;
    delete persistedStage.cloudProvider;
    delete persistedStage.cloudProviderType;
    const originalStage = cloneDeep(persistedStage);
    requested.stages = [{ refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any, persistedStage];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);
    (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(Promise.resolve([account('ecs')]) as any);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const inferredStage = (wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).stages[1];
    expect(inferredStage).toEqual({
      ...originalStage,
      type: 'customDestroyServerGroup',
      cloudProvider: 'ecs',
      cloudProviderType: 'ecs',
    } as any);
    expect(wrapper.find(BaseProviderStageConfig).prop('readOnly')).toBe(true);
    expect(wrapper.find(BaseProviderStageConfig).prop('selectedProvider')).toBe('ecs');
    expect(wrapper.find('.ecs-stage-config').exists()).toBe(true);
    expect(wrapper.find('.aws-stage-config').exists()).toBe(false);
    expect(providerRenderStates.length).toBeGreaterThan(0);
    expect(providerRenderStates).not.toContain(false);
    expect(wrapper.find('.pipeline-configurer').prop('data-revision')).toBe(1);
    expect(wrapper.text()).toContain('Save Changes');

    wrapper.setProps({ app });
    await flush();
    wrapper.update();

    expect(wrapper.find('.pipeline-configurer').prop('data-revision')).toBe(1);
    expect(providerRenderStates).not.toContain(false);

    wrapper.unmount();
  });

  [
    { presentField: 'cloudProvider', missingField: 'cloudProviderType' },
    { presentField: 'cloudProviderType', missingField: 'cloudProvider' },
  ].forEach(({ presentField, missingField }) => {
    it(`normalizes a persisted stage with only ${presentField} before rendering its provider implementation`, async () => {
      registerBaseProviderStages();
      const requested = pipeline('target-id', 'Requested Pipeline');
      requested.stages = [
        {
          refId: '1',
          name: 'Destroy Server Group',
          type: 'destroyServerGroup',
          requisiteStageRefIds: [],
          [presentField]: 'ecs',
        } as any,
      ];
      const app = createApp([requested]);
      $stateParams.pipelineId = requested.id;
      showStageConfig(requested.id);
      (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(
        Promise.resolve([account('aws'), account('ecs')]) as any,
      );

      const wrapper = mount(<PipelineConfigPage app={app} />);
      await flush();
      wrapper.update();

      const updatedPipeline = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
      expect(updatedPipeline.stages[0].cloudProvider).toBe('ecs');
      expect(updatedPipeline.stages[0].cloudProviderType).toBe('ecs');
      expect(updatedPipeline.stages[0][missingField]).toBe('ecs');
      expect(wrapper.find(BaseProviderStageConfig).prop('readOnly')).toBe(true);
      expect(
        wrapper
          .find(ReactSelectInput)
          .filterWhere((node) => node.prop('name') === 'cloudProviderType')
          .exists(),
      ).toBe(false);
      expect(wrapper.find('.ecs-stage-config').exists()).toBe(true);
      expect(providerRenderStates).not.toContain(false);
      expect(wrapper.find('.pipeline-configurer').prop('data-revision')).toBe(1);
      expect(wrapper.text()).toContain('Save Changes');

      wrapper.unmount();
    });
  });

  it('normalizes the same persisted stage again if JSON editing makes its provider fields incoherent', async () => {
    registerBaseProviderStages();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      {
        refId: '1',
        name: 'Destroy Server Group',
        type: 'destroyServerGroup',
        requisiteStageRefIds: [],
        cloudProvider: 'ecs',
      } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(
      Promise.resolve([account('aws'), account('ecs')]) as any,
    );
    spyOn(ReactModal, 'show').and.callFake((_component, props: { stage: IStage }) => {
      delete props.stage.cloudProviderType;
      return Promise.resolve();
    });

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();
    expect(wrapper.find('.pipeline-configurer').prop('data-revision')).toBe(1);

    const editJsonButton = wrapper.find('button').filterWhere((node) => node.text().includes('Edit stage as JSON'));
    await act(async () => {
      editJsonButton.prop('onClick')();
      await flush();
    });
    wrapper.update();

    const normalizedStage = (wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).stages[0];
    expect(normalizedStage.cloudProvider).toBe('ecs');
    expect(normalizedStage.cloudProviderType).toBe('ecs');
    expect(wrapper.find('.pipeline-configurer').prop('data-revision')).toBe(3);
    expect(providerRenderStates).not.toContain(false);

    wrapper.unmount();
  });

  it('waits for provider selection, switches provider implementations, and updates once per selection', async () => {
    registerBaseProviderStages();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      {
        refId: '1',
        name: 'Destroy Server Group',
        type: 'destroyServerGroup',
        isNew: true,
        requisiteStageRefIds: [],
      } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(
      Promise.resolve([account('aws'), account('ecs')]) as any,
    );

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    expect(wrapper.find(BaseProviderStageConfig).prop('providers')).toEqual(['aws', 'ecs']);
    expect(wrapper.find('.aws-stage-config').exists()).toBe(false);
    expect(wrapper.find('.ecs-stage-config').exists()).toBe(false);
    expect(wrapper.find(StageConfigWrapper).exists()).toBe(false);
    const initialRevision = wrapper.find('.pipeline-configurer').prop('data-revision') as number;

    const providerSelect = () =>
      wrapper.find(ReactSelectInput).filterWhere((node) => node.prop('name') === 'cloudProviderType');
    await act(async () => {
      providerSelect().prop('onChange')({ target: { value: 'ecs' } } as any);
      await flush();
    });
    wrapper.update();

    expect(wrapper.find('.ecs-stage-config').exists()).toBe(true);
    expect(wrapper.find('.aws-stage-config').exists()).toBe(false);
    expect(wrapper.find('.pipeline-configurer').prop('data-revision')).toBe(initialRevision + 1);
    expect(wrapper.find('.stage-details').first().childAt(0).is(BaseProviderStageConfig)).toBe(true);
    expect(wrapper.find('.stage-details').first().childAt(1).is(StageConfigWrapper)).toBe(true);

    await act(async () => {
      providerSelect().prop('onChange')({ target: { value: 'aws' } } as any);
      await flush();
    });
    wrapper.update();

    const updatedPipeline = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
    expect(updatedPipeline.stages[0]).toEqual(
      jasmine.objectContaining({ cloudProvider: 'aws', cloudProviderType: 'aws' }),
    );
    expect(wrapper.find('.aws-stage-config').exists()).toBe(true);
    expect(wrapper.find('.ecs-stage-config').exists()).toBe(false);
    expect(wrapper.find('.pipeline-configurer').prop('data-revision')).toBe(initialRevision + 2);
    expect(() => wrapper.find(BaseProviderStageConfig).prop('onProviderChange')('gcp')).toThrowError(
      /destroyServerGroup.*gcp/,
    );

    wrapper.unmount();
  });

  it('removes ECS-specific fields while preserving common controls when switching from ECS to AWS', async () => {
    registerBaseProviderStages();
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any,
      providerStage('ecs'),
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);
    (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(
      Promise.resolve([account('aws'), account('ecs')]) as any,
    );

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    await act(async () => {
      wrapper.find(BaseProviderStageConfig).prop('onProviderChange')('aws');
      await flush();
    });
    wrapper.update();

    const switchedStage = (wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).stages[1];
    expectCommonFieldsPreserved(switchedStage);
    expectProviderFieldsRemoved(switchedStage, 'ecs');
    expect(switchedStage).toEqual(
      jasmine.objectContaining({ type: 'destroyServerGroup', cloudProvider: 'aws', cloudProviderType: 'aws' }),
    );
    expect(wrapper.find('.aws-stage-config').exists()).toBe(true);
    expect(wrapper.find('.ecs-stage-config').exists()).toBe(false);

    wrapper.unmount();
  });

  it('removes AWS-specific fields while preserving common controls when switching from AWS to ECS', async () => {
    registerBaseProviderStages('customDestroyServerGroup');
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any,
      providerStage('aws'),
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);
    (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(
      Promise.resolve([account('aws'), account('ecs')]) as any,
    );

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    await act(async () => {
      wrapper.find(BaseProviderStageConfig).prop('onProviderChange')('ecs');
      await flush();
    });
    wrapper.update();

    const switchedStage = (wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).stages[1];
    expectCommonFieldsPreserved(switchedStage);
    expectProviderFieldsRemoved(switchedStage, 'aws');
    expect(switchedStage).toEqual(
      jasmine.objectContaining({
        type: 'customDestroyServerGroup',
        cloudProvider: 'ecs',
        cloudProviderType: 'ecs',
      }),
    );
    expect(wrapper.find('.ecs-stage-config').exists()).toBe(true);
    expect(wrapper.find('.aws-stage-config').exists()).toBe(false);

    wrapper.unmount();
  });

  it('retains only stage identity fields when changing stage type', async () => {
    registerBaseProviderStages();
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any,
      providerStage('ecs'),
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);
    (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(Promise.resolve([account('ecs')]) as any);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const typeSelect = wrapper.find(ReactSelectInput).filterWhere((node) => node.prop('name') === 'type');
    await act(async () => {
      typeSelect.prop('onChange')({ target: { value: 'wait' } } as any);
      await flush();
    });
    wrapper.update();

    const changedStage = (wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).stages[1];
    expect(changedStage).toEqual({
      refId: '2',
      requisiteStageRefIds: ['1'],
      isNew: true,
      name: 'Custom destroy name',
      type: 'wait',
    } as any);

    wrapper.unmount();
  });

  it('persists an implementation-specific stage key when selecting its provider', async () => {
    registerBaseProviderStages('customDestroyServerGroup');
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      {
        refId: '1',
        name: 'Destroy Server Group',
        type: 'destroyServerGroup',
        isNew: true,
        requisiteStageRefIds: [],
      } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(Promise.resolve([account('ecs')]) as any);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const updatedPipeline = wrapper.find(PipelineGraph).prop('pipeline') as IPipeline;
    expect(updatedPipeline.stages[0]).toEqual(
      jasmine.objectContaining({
        type: 'customDestroyServerGroup',
        cloudProvider: 'ecs',
        cloudProviderType: 'ecs',
      }),
    );
    expect(wrapper.find('.ecs-stage-config').exists()).toBe(true);

    wrapper.unmount();
  });

  it('preserves direct rendering for non-base React stage configs', async () => {
    Registry.pipeline.registerStage({
      key: 'regular',
      label: 'Regular',
      component: RegularStageConfig,
    } as IStageTypeConfig);
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Regular', type: 'regular', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    expect(wrapper.find('.regular-stage-config').exists()).toBe(true);
    expect(wrapper.find(BaseProviderStageConfig).exists()).toBe(false);

    wrapper.unmount();
  });

  it('applies cloned defaults, alias, and default label once through the dirty-state update path', async () => {
    const defaults = { nested: { value: 'default value' }, credentials: 'default-account' };
    Registry.pipeline.registerStage({
      key: 'regularWithDefaults',
      label: 'Regular With Defaults',
      alias: 'legacyRegular',
      addAliasToConfig: true,
      defaults,
      component: RegularStageConfig,
    } as IStageTypeConfig);
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: '', type: 'regularWithDefaults', isNew: true, requisiteStageRefIds: [] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const defaultedStage = (wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).stages[0] as any;
    expect(defaultedStage).toEqual(
      jasmine.objectContaining({
        name: 'Regular With Defaults',
        alias: 'legacyRegular',
        nested: { value: 'default value' },
        credentials: 'default-account',
      }),
    );
    expect(defaultedStage.nested).not.toBe(defaults.nested);
    expect(wrapper.find('.pipeline-configurer').prop('data-revision')).toBe(1);
    expect(wrapper.text()).toContain('Save Changes');

    wrapper.setProps({ app });
    await flush();
    wrapper.update();

    expect(wrapper.find('.pipeline-configurer').prop('data-revision')).toBe(1);

    wrapper.unmount();
  });

  it('preserves strategy stage type filtering after accounts load', async () => {
    Registry.pipeline.registerStage({ key: 'pipelineOnly', label: 'Pipeline Only' } as IStageTypeConfig);
    Registry.pipeline.registerStage({
      key: 'strategyStage',
      label: 'Strategy Stage',
      strategy: true,
    } as IStageTypeConfig);
    const requested = pipeline('target-id', 'Requested Strategy');
    requested.strategy = true;
    requested.stages = [{ refId: '1', name: '', type: '', isNew: true, requisiteStageRefIds: [] } as any];
    const app = createApp([], [requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const options = wrapper
      .find(ReactSelectInput)
      .filterWhere((node) => node.prop('name') === 'type')
      .prop('options') as any[];
    expect(options.map((option) => option.value)).toEqual(['strategyStage']);

    wrapper.unmount();
  });

  it('ignores an old account request that resolves after the application changes', async () => {
    registerBaseProviderStages();
    const oldPipeline = pipeline('target-id', 'Old Pipeline');
    oldPipeline.stages = [{ refId: '1', name: 'Destroy Server Group', type: 'destroyServerGroup', isNew: true } as any];
    const newPipeline = pipeline('target-id', 'New Pipeline');
    newPipeline.stages = [{ refId: '1', name: 'Destroy Server Group', type: 'destroyServerGroup', isNew: true } as any];
    const oldApp = createApp([oldPipeline]);
    const newApp = createApp([newPipeline]);
    $stateParams.pipelineId = 'target-id';
    showStageConfig('target-id');
    let resolveOldAccounts: (accounts: IAccountDetails[]) => void;
    let resolveNewAccounts: (accounts: IAccountDetails[]) => void;
    const oldRequest = new Promise<IAccountDetails[]>((resolve) => (resolveOldAccounts = resolve));
    const newRequest = new Promise<IAccountDetails[]>((resolve) => (resolveNewAccounts = resolve));
    (AccountService.applicationAccounts as jasmine.Spy).and.callFake((requestedApp) =>
      requestedApp === oldApp ? oldRequest : newRequest,
    );

    const wrapper = mount(<PipelineConfigPage app={oldApp} />);
    await flush();
    wrapper.setProps({ app: newApp });
    await flush();

    await act(async () => {
      resolveNewAccounts([account('ecs')]);
      await flush();
    });
    wrapper.update();
    expect(wrapper.find(BaseProviderStageConfig).prop('providers')).toEqual(['ecs']);

    await act(async () => {
      resolveOldAccounts([account('aws')]);
      await flush();
    });
    wrapper.update();
    expect(wrapper.find(BaseProviderStageConfig).prop('providers')).toEqual(['ecs']);
    expect(wrapper.find('.ecs-stage-config').exists()).toBe(true);
    expect(wrapper.find('.aws-stage-config').exists()).toBe(false);

    wrapper.unmount();
  });

  it('marks a copied provider stage as new so its provider remains editable', async () => {
    registerBaseProviderStages();
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    const copiedStage = providerStage('ecs');
    copiedStage.isNew = false;
    $stateParams.pipelineId = requested.id;
    (AccountService.applicationAccounts as jasmine.Spy).and.returnValue(
      Promise.resolve([account('aws'), account('ecs')]) as any,
    );
    spyOn(ReactModal, 'show').and.returnValue(Promise.resolve(copiedStage));

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    const copyButton = wrapper.find('button').filterWhere((node) => node.text().includes('Copy an existing stage'));
    await act(async () => {
      copyButton.prop('onClick')();
      await flush();
    });
    wrapper.update();

    const copied = (wrapper.find(PipelineGraph).prop('pipeline') as IPipeline).stages[1];
    expect(copied.isNew).toBe(true);
    expect(wrapper.find(BaseProviderStageConfig).prop('readOnly')).toBe(false);
    expect(
      wrapper
        .find(ReactSelectInput)
        .filterWhere((node) => node.prop('name') === 'cloudProviderType')
        .exists(),
    ).toBe(true);

    wrapper.unmount();
  });

  it('does not fall back to the first pipeline or match by name when the id is missing', async () => {
    const app = createApp([pipeline('first-id', 'missing-id')]);
    $stateParams.pipelineId = 'missing-id';

    const wrapper = mount(<PipelineConfigPage app={app} />);
    await flush();
    wrapper.update();

    expect(wrapper.find('.pipeline-configurer').exists()).toBe(false);
    expect(wrapper.text()).toContain('No pipeline found with that name.');

    wrapper.unmount();
  });
});
