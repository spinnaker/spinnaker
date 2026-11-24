import type { Mock } from 'vitest';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { RenderResult } from '@testing-library/react';
import { setupUser } from '../../utils/testUtils/userEvent';
import ace from 'ace-builds';
import $ from 'jquery';
import { cloneDeep } from 'lodash';
import React from 'react';

import { AccountService } from '../../account/AccountService';
import type { IAccountDetails } from '../../account/AccountService';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { ApplicationDataSourceRegistry } from '../../application/service/ApplicationDataSourceRegistry';
import { ApplicationReader } from '../../application/service/ApplicationReader';
import type { DeckRuntime } from '../../bootstrap/DeckRuntime';
import { createDeckRuntime } from '../../bootstrap/DeckRuntime';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { ViewStateCache } from '../../cache';
import { SETTINGS } from '../../config';
import type { IPipeline, IStage, IStageTypeConfig, ITriggerTypeConfig } from '../../domain';
import { ReactModal } from '../../presentation/ReactModal';
import { Registry } from '../../registry';
import { getFormGroupByLabel } from '../../utils/testUtils/rtl';
import {
  applyStageConfigDefaults,
  COMMON_STAGE_FIELDS,
  PipelineConfigPageComponent,
  STAGE_IDENTITY_FIELDS,
} from './PipelineConfigPage';
import { PipelineConfigService } from './services/PipelineConfigService';
import { ScriptStageConfig, validate as validateScriptStage } from './stages/script/ScriptStageConfig';
import { ConfigurePipelineTemplateModal } from './templates/ConfigurePipelineTemplateModal';
import { PipelineTemplateReader } from './templates/PipelineTemplateReader';

describe('PipelineConfigPage', () => {
  let $stateParams: { executionId?: string; new?: string; pipelineId?: string };
  let fiatEnabled: boolean;
  let providerRenderStates: boolean[];
  let router: UIRouterReact;
  let runtime: DeckRuntime;
  let transitionCleanup: Mock;
  let transitionOnBefore: Mock;

  const PipelineConfigPage = ({ app, className }: { app: any; className?: string }) => (
    <UIRouterContext.Provider value={router}>
      <DeckRuntimeContext.Provider value={runtime}>
        <PipelineConfigPageComponent
          app={app}
          className={className}
          router={router}
          stateParams={$stateParams}
          stateService={router.stateService}
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
      ...ApplicationDataSourceRegistry.getDataSources(),
    );
    app.pipelineConfigs.data = pipelines;
    app.strategyConfigs.data = strategies;
    vi.spyOn(app.pipelineConfigs, 'activate');
    vi.spyOn(app.pipelineConfigs, 'refresh').mockReturnValue(Promise.resolve(pipelines) as any);
    vi.spyOn(app.strategyConfigs, 'activate');
    vi.spyOn(app.strategyConfigs, 'refresh').mockReturnValue(Promise.resolve(strategies) as any);
    return app;
  };

  const flush = async () => {
    await act(async () => {
      await Promise.resolve();
      await new Promise((resolve) => setTimeout(resolve, 0));
      await Promise.resolve();
    });
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
      description: 'Pauses execution before continuing.',
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
      expect.objectContaining({
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

  const renderPage = (app: any, className?: string) => render(<PipelineConfigPage app={app} className={className} />);

  const rerenderPage = (rendered: RenderResult, app: any, className?: string) =>
    rendered.rerender(<PipelineConfigPage app={app} className={className} />);

  const waitForPipeline = async (name: string) => screen.findByRole('heading', { name: new RegExp(name) });

  const field = (label: string) => getFormGroupByLabel(label);

  const select = (label: string) => within(field(label)).getByRole('combobox');

  const choose = async (user: ReturnType<typeof setupUser>, label: string, option: string) => {
    await user.click(select(label));
    await user.click(await screen.findByRole('option', { name: new RegExp(`^${option}(?:\\s|$)`) }));
  };

  const graph = () => document.querySelector('.pipeline-config-graph') as HTMLElement;

  const graphLabel = (name: string) => within(graph()).getByText(name, { selector: '.label-body a' });

  const revision = () => Number(document.querySelector('.pipeline-configurer')?.getAttribute('data-revision'));

  const saveAndGetPipeline = async (user: ReturnType<typeof setupUser>) => {
    const savePipeline = vi.spyOn(PipelineConfigService, 'savePipeline').mockResolvedValue();
    await user.click(screen.getByRole('button', { name: /Save Changes/ }));
    await waitFor(() => expect(savePipeline).toHaveBeenCalledTimes(1));
    return savePipeline.mock.calls[0][0];
  };

  // Renders each modal the page opens, and settles the page's promise when the modal closes
  const renderModals = () =>
    vi.spyOn(ReactModal, 'show').mockImplementation(
      ((ModalComponent: React.ComponentType<any>, props: any) =>
        new Promise((resolve, reject) => {
          const modal = render(
            <ModalComponent
              {...props}
              closeModal={(result?: any) => {
                modal.unmount();
                resolve(result);
              }}
              dismissModal={(reason?: any) => {
                modal.unmount();
                reject(reason);
              }}
            />,
          );
        })) as any,
    );

  const setJsonEditorValue = (update: (json: any) => any) => {
    const editor = ace.edit(document.querySelector('.ace-editor') as HTMLElement);
    act(() => {
      editor.setValue(JSON.stringify(update(JSON.parse(editor.getValue()))));
    });
  };

  // Like Formik-based editors, captures the stage when it mounts
  const MountedStageConfig = ({ stage }: { stage: any }) => {
    const [value] = React.useState(stage.value);
    return <div className="mounted-stage-config">{value}</div>;
  };

  const registerMountedStage = () =>
    Registry.pipeline.registerStage({
      key: 'mounted',
      label: 'Mounted',
      component: MountedStageConfig,
    } as IStageTypeConfig);

  const mountedStageValue = () => document.querySelector('.mounted-stage-config')?.textContent;

  const jsonEditorValue = () => JSON.parse(ace.edit(document.querySelector('.ace-editor') as HTMLElement).getValue());

  const openPipelineAction = async (user: ReturnType<typeof setupUser>, action: string) => {
    await user.click(screen.getByRole('button', { name: 'Pipeline Actions' }));
    await user.click(screen.getByText(action, { selector: 'a' }));
  };

  const editPipelineJson = async (user: ReturnType<typeof setupUser>, update: (json: any) => any) => {
    await openPipelineAction(user, 'Edit as JSON');
    setJsonEditorValue(update);
    await user.click(screen.getByRole('button', { name: /Update Pipeline/ }));
  };

  const EditingStageConfig = ({ updateStageField }: { updateStageField: (changes: any) => void }) => (
    <button onClick={() => updateStageField({ value: 'edited' })}>Edit value</button>
  );

  let localStageMounts = 0;

  // Like Formik-based editors, keeps its own copy of the stage
  const LocalStateStageConfig = ({
    stage,
    updateStageField,
  }: {
    stage: any;
    updateStageField: (changes: any) => void;
  }) => {
    const [value, setValue] = React.useState(stage.value);
    React.useEffect(() => {
      localStageMounts++;
    }, []);
    return (
      <input
        aria-label="Local value"
        value={value}
        onChange={(event) => {
          setValue(event.target.value);
          updateStageField({ value: event.target.value });
        }}
      />
    );
  };

  const registerLocalStage = () => {
    localStageMounts = 0;
    Registry.pipeline.registerStage({
      key: 'local',
      label: 'Local',
      component: LocalStateStageConfig,
    } as IStageTypeConfig);
  };

  const localStageInput = () => screen.queryByRole('textbox', { name: 'Local value' }) as HTMLInputElement;

  const typeLocalValue = async (user: ReturnType<typeof setupUser>, value: string) => {
    await user.clear(localStageInput());
    await user.type(localStageInput(), value);
  };

  const executionOptions = () => screen.queryByText(/Disable concurrent pipeline executions/);

  // Like Front50 with stale checks enabled: rejects a save with a stale updateTs, and stores a new updateTs for
  // each accepted save
  const staleCheckingSave = (stored: IPipeline) => {
    const staleSaves: IPipeline[] = [];
    const submittedUpdateTs: number[] = [];
    const savePipeline = vi.spyOn(PipelineConfigService, 'savePipeline').mockImplementation((toSave: IPipeline) => {
      submittedUpdateTs.push(toSave.updateTs);
      if (toSave.updateTs !== stored.updateTs) {
        staleSaves.push(toSave);
        return Promise.reject({ data: { message: 'The submitted pipeline is stale.' } });
      }
      stored.updateTs += 1;
      return Promise.resolve();
    });
    return { savePipeline, staleSaves, submittedUpdateTs };
  };

  const maxConcurrentInput = () => screen.getByRole('spinbutton');

  // Keeps its value in the trigger form's Formik state, and counts its mounts
  const registerTestTrigger = () => {
    const mounts = { count: 0 };
    const TestTrigger = ({ formik }: { formik: any }) => {
      React.useEffect(() => {
        mounts.count++;
      }, []);
      return (
        <input
          aria-label="Test trigger value"
          value={formik.values.value || ''}
          onChange={(event) => formik.setFieldValue('value', event.target.value)}
        />
      );
    };
    // Registered under a key allowed by SETTINGS.triggerTypes
    Registry.pipeline.registerTrigger({ key: 'webhook', label: 'Test', component: TestTrigger } as ITriggerTypeConfig);
    return mounts;
  };

  const testTriggerInputs = () => screen.getAllByRole('textbox', { name: 'Test trigger value' }) as HTMLInputElement[];

  const testTriggerValues = () => testTriggerInputs().map((input) => input.value);

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
      expect.objectContaining({ name: 'Regular', alias: 'legacyRegular', nested: { value: 'default value' } }),
    );
    expect(stage.nested).not.toBe(defaults.nested);
    expect(defaults).toEqual({ nested: { value: 'default value' } });
    expect(applyStageConfigDefaults(stage, config)).toBe(false);
  });

  beforeEach(async () => {
    vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(320);
    $stateParams = {};
    ApplicationDataSourceRegistry.clearDataSources();
    ApplicationDataSourceRegistry.registerDataSource({ key: 'pipelineConfigs', lazy: true, defaultData: [] });
    ApplicationDataSourceRegistry.registerDataSource({ key: 'strategyConfigs', lazy: true, defaultData: [] });
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    router.stateRegistry.register({ name: 'home', url: '/' });
    router.stateRegistry.register({ name: 'home.application', url: 'application' });
    router.stateRegistry.register({ name: 'home.application.pipelines', url: '/pipelines' });
    router.stateRegistry.register({ name: 'home.application.pipelines.configure', url: '/configure' });
    router.stateRegistry.register({ name: 'home.application.pipelines.executions', url: '/executions' });
    await router.stateService.go('home.application.pipelines.configure', {}, { location: false });
    runtime = createDeckRuntime(router);
    transitionCleanup = vi.fn();
    transitionOnBefore = vi.spyOn(router.transitionService, 'onBefore').mockReturnValue(transitionCleanup);
    fiatEnabled = SETTINGS.feature.fiatEnabled;
    providerRenderStates = [];
    Registry.reinitialize();
    ViewStateCache.get('pipelineConfig').removeAll();
    vi.spyOn(AccountService, 'applicationAccounts').mockImplementation(() => Promise.resolve([account('aws')]) as any);
    vi.spyOn(ApplicationReader, 'getApplicationPermissions').mockReturnValue(Promise.resolve({}) as any);
    vi.spyOn(runtime.services.executionService, 'getExecutionsForConfigIds').mockReturnValue(Promise.resolve([]));
  });

  afterEach(() => {
    cleanup();
    runtime.dispose();
    router.dispose();
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled };
    Registry.reinitialize();
    ApplicationDataSourceRegistry.clearDataSources();
    ViewStateCache.get('pipelineConfig').removeAll();
  });

  it('refreshes configs and renders the requested pipeline configurer by id', async () => {
    const requested = pipeline('target-id', 'Requested Pipeline');
    const app = createApp([pipeline('first-id', 'First Pipeline'), requested]);
    const refresh = deferred<IPipeline[]>();
    (app.pipelineConfigs.refresh as Mock).mockReturnValue(refresh.promise);
    $stateParams.pipelineId = requested.id;

    const rendered = renderPage(app, 'flex-fill');
    expect(screen.getByText('Loading pipeline configuration...')).toBeVisible();

    refresh.resolve(app.pipelineConfigs.data);
    await waitForPipeline('Requested Pipeline');

    expect(app.pipelineConfigs.activate).toHaveBeenCalled();
    expect(app.pipelineConfigs.refresh).toHaveBeenCalled();
    expect(document.querySelector('.pipeline-configurer')).toBeInTheDocument();
    rendered.unmount();

    const failingApp = createApp([requested]);
    (failingApp.pipelineConfigs.refresh as Mock).mockRejectedValue(new Error('refresh failed'));
    renderPage(failingApp);
    expect(await screen.findByText('Could not load pipeline configuration.')).toBeVisible();
  });

  it('uses the injected router for transition guarding and back navigation', async () => {
    const user = setupUser();
    const requested = pipeline('target-id', 'Requested Pipeline');
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;

    const rendered = renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await user.click(document.querySelector('.btn-configure') as HTMLElement);

    await waitFor(() => expect(router.stateService.current.name).toBe('home.application.pipelines.executions'));
    expect(transitionOnBefore).toHaveBeenCalledWith({}, expect.any(Function));

    rendered.unmount();
    expect(transitionCleanup).toHaveBeenCalled();
  });

  it('renders the React pipeline config layout wrappers', async () => {
    const app = createApp([pipeline('target-id', 'Requested Pipeline')]);
    $stateParams.pipelineId = 'target-id';

    renderPage(app, 'flex-fill');
    await waitForPipeline('Requested Pipeline');

    const page = document.querySelector('.pipeline-config-page');
    expect(page).toHaveClass('container-fluid', 'full-width', 'flex-fill');
    expect(page?.querySelector('.col-md-10.col-md-offset-1 .pipeline-configurer')).toBeInTheDocument();
    expect(page?.querySelector('.pipeline-config-view .row.horizontal > .col-md-12')).toBeInTheDocument();
    expect(document.querySelector('pipeline-configurer')).not.toBeInTheDocument();
  });

  it('uses the pipeline config page as the scroll container for page navigation', async () => {
    const app = createApp([pipeline('target-id', 'Requested Pipeline')]);
    $stateParams.pipelineId = 'target-id';
    const closest = vi.spyOn($.fn, 'closest');

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    expect(closest).toHaveBeenCalledWith('.pipeline-config-page');
    expect(document.querySelector('.page-navigator')?.closest('.pipeline-config-page')).toBeInTheDocument();
  });

  it('uses the custom stage type selector when adding a stage', async () => {
    const user = setupUser();
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await user.click(screen.getByRole('button', { name: /Add stage/ }));

    const typeSelect = await waitFor(() => select('Type'));
    expect(typeSelect.closest('.pipeline-stage-type-select')).not.toHaveClass('input-sm');
    await user.click(typeSelect);
    expect(document.querySelector('.VirtualSelectGrid')).toBeInTheDocument();
    expect((await screen.findAllByRole('option')).map((option) => option.textContent)).toEqual([
      expect.stringContaining('Manual Judgment'),
      expect.stringContaining('Wait'),
    ]);
    expect(screen.getByText('Pauses execution before continuing.')).toBeVisible();
    expect(screen.getByText('Wait').closest('.stage-choice')).toBeInTheDocument();
    const initialActiveDescendant = typeSelect.getAttribute('aria-activedescendant');
    expect(initialActiveDescendant).toBeTruthy();
    expect(document.getElementById(initialActiveDescendant as string)).toHaveTextContent('Manual Judgment');

    fireEvent.keyDown(typeSelect, { key: 'ArrowDown', keyCode: 40, which: 40 });
    const navigatedActiveDescendant = typeSelect.getAttribute('aria-activedescendant');
    expect(navigatedActiveDescendant).not.toBe(initialActiveDescendant);
    expect(document.getElementById(navigatedActiveDescendant as string)).toHaveTextContent('Wait');

    await user.type(typeSelect, 'wait');
    expect(screen.queryByRole('option', { name: /Manual Judgment/ })).not.toBeInTheDocument();
    expect(screen.getByRole('option', { name: /^Wait/ })).toBeVisible();
    const filteredActiveDescendant = typeSelect.getAttribute('aria-activedescendant');
    expect(filteredActiveDescendant).not.toBe(navigatedActiveDescendant);
    expect(document.getElementById(filteredActiveDescendant as string)).toBe(
      screen.getByRole('option', { name: /^Wait/ }),
    );
    fireEvent.keyDown(typeSelect, { key: 'Enter', keyCode: 13, which: 13 });

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0]).toEqual(
      expect.objectContaining({ refId: '1', name: 'Wait', type: 'wait', requisiteStageRefIds: [] }),
    );
    expect(document.querySelector('.pipeline-stage-config-heading select')).not.toBeInTheDocument();
  });

  it('keeps focus on a stage field after the new-stage type selector initially autofocuses', async () => {
    const user = setupUser();
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Build', type: 'wait', isNew: true, requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    const typeInput = select('Type');
    const stageName = within(field('Stage Name')).getByRole('textbox');
    expect(document.activeElement).toBe(typeInput);

    await user.click(stageName);
    await user.clear(stageName);
    await user.type(stageName, 'Updated Build');

    expect(document.activeElement).toBe(stageName);
    expect(stageName).toHaveValue('Updated Build');
  });

  it('uses the custom multi selector for stage dependencies', async () => {
    const user = setupUser();
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any,
      { refId: '2', name: 'Deploy', type: 'wait', requisiteStageRefIds: [] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    const dependencySelect = select('Depends On');
    expect(dependencySelect.closest('.pipeline-stage-dependency-select')).not.toHaveClass('input-sm');
    await user.click(dependencySelect);
    expect(screen.getByRole('option', { name: 'Build' })).toBeVisible();
    await user.click(screen.getByRole('option', { name: 'Build' }));

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[1].requisiteStageRefIds).toEqual(['1']);
    expect(document.querySelector('.pipeline-stage-config-heading select[multiple]')).not.toBeInTheDocument();
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

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    ['Stage Name', 'Depends On'].forEach((label) => {
      const group = field(label);
      expect(group.querySelector('label')).toHaveClass('col-md-2');
      expect(group.querySelector('label + div')).toHaveClass('col-md-9');
    });
  });

  it('replaces the graph pipeline when stage dependencies change', async () => {
    const user = setupUser();
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any,
      { refId: '2', name: 'Deploy', type: 'wait', requisiteStageRefIds: [] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    const initialLinks = Array.from(graph().querySelectorAll('path.link')).map((link) => link.getAttribute('d'));

    await choose(user, 'Depends On', 'Build');

    await waitFor(() =>
      expect(Array.from(graph().querySelectorAll('path.link')).map((link) => link.getAttribute('d'))).not.toEqual(
        initialLinks,
      ),
    );
    expect(graphLabel('Deploy').closest('g.active')).toBeInTheDocument();
  });

  it('replaces the graph pipeline when stage fields change', async () => {
    const user = setupUser();
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    expect(graphLabel('Build')).toBeVisible();

    const stageName = within(field('Stage Name')).getByRole('textbox');
    await user.clear(stageName);
    await user.type(stageName, 'Bake');

    await waitFor(() => expect(graphLabel('Bake')).toBeVisible());
    expect(within(graph()).queryByText('Build', { selector: '.label-body a' })).not.toBeInTheDocument();
  });

  it('renders direct React common execution controls for stages', async () => {
    const user = setupUser();
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [], failOnFailedExpressions: false } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    await user.click(within(field('Fail on Failed Expressions')).getByRole('checkbox'));
    await user.click(within(field('Conditional on Expression')).getByRole('checkbox'));
    const expression = within(field('Conditional on Expression')).getByRole('textbox');
    fireEvent.change(expression, { target: { value: '${ parameters.deploy }' } });

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0].failOnFailedExpressions).toBe(true);
    expect(saved.stages[0].stageEnabled).toEqual({ type: 'expression', expression: '${ parameters.deploy }' });
  });

  it('preserves existing stage notifications when generic notification sending is disabled', async () => {
    const user = setupUser();
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

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await user.click(screen.getByRole('checkbox', { name: 'Send notifications for this stage' }));

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0].sendNotifications).toBeUndefined();
    expect(saved.stages[0].notifications).toEqual([{ type: 'email', address: 'team@example.com' }]);
  });

  it('renders manual judgment authorized groups from application permissions', async () => {
    const user = setupUser();
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    (ApplicationReader.getApplicationPermissions as Mock).mockReturnValue(
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

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await user.click(select('Authorized Groups'));

    expect(screen.getByRole('option', { name: 'readers' })).toBeVisible();
    expect(screen.getByRole('option', { name: 'writers' })).toBeVisible();
    expect(screen.getByRole('option', { name: 'executors' })).toBeVisible();
    await user.click(screen.getByRole('option', { name: 'executors' }));

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0].selectedStageRoles).toEqual(['writers', 'executors']);
  });

  it('shows template configuration controls', async () => {
    const requested = templatedV1();
    const plan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(plan);

    renderPage(app);

    expect(await screen.findByRole('button', { name: /Configure Template/ })).toBeVisible();
  });

  it('opens the shared React modal with a clone and makes successful configuration revertible', async () => {
    const user = setupUser();
    const requested = templatedV1();
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    (originalPlan as any).executionId = 'rendered-execution-id';
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    $stateParams.executionId = 'route-execution-id';
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(originalPlan);
    const modalResult = deferred<{ plan: IPipeline; config: IPipeline }>();
    const showModal = vi.spyOn(ReactModal, 'show').mockReturnValue(modalResult.promise);

    renderPage(app);
    const configure = await screen.findByRole('button', { name: /Configure Template/ });
    await user.click(configure);
    const modalProps = showModal.mock.lastCall[1] as any;

    expect(showModal.mock.lastCall[0]).toBe(ConfigurePipelineTemplateModal);
    expect(modalProps).toEqual(
      expect.objectContaining({
        application: app,
        executionId: 'rendered-execution-id',
        isNew: requested.isNew,
        pipelineId: requested.id,
      }),
    );
    expect(modalProps.pipelineTemplateConfig).toEqual(requested);
    expect(modalProps.pipelineTemplateConfig).not.toBe(requested);
    modalProps.pipelineTemplateConfig.name = 'mutated clone';
    expect(screen.getByRole('heading', { name: /Template Pipeline/ })).toBeVisible();

    modalResult.resolve({
      plan: { ...originalPlan, name: 'Configured Pipeline' },
      config: { ...requested, isNew: true, name: 'Configured Pipeline' },
    });
    await waitForPipeline('Configured Pipeline');
    expect(screen.getByRole('button', { name: /Save Changes/ })).toBeVisible();

    await user.click(screen.getByRole('button', { name: /Revert/ }));
    expect(await waitForPipeline('Template Pipeline')).toBeVisible();
    expect(screen.queryByRole('button', { name: /Revert/ })).not.toBeInTheDocument();
  });

  it('applies a template modal result after execution enrichment replaces the same pipeline model', async () => {
    const user = setupUser();
    const requested = templatedV1(false, 'https://templates.example/{{ execution.id }}');
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    (originalPlan as any).executionId = 'rendered-execution-id';
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(originalPlan);
    const executionEnrichment = deferred<any[]>();
    (runtime.services.executionService.getExecutionsForConfigIds as Mock).mockReturnValue(executionEnrichment.promise);
    const modalResult = deferred<{ plan: IPipeline; config: IPipeline }>();
    vi.spyOn(ReactModal, 'show').mockReturnValue(modalResult.promise);

    renderPage(app);
    const configure = await screen.findByRole('button', { name: /Configure Template/ });
    await user.click(configure);

    executionEnrichment.resolve([{ id: 'rendered-execution-id', name: 'Enriched execution', stages: [], trigger: {} }]);
    await screen.findByText(/Enriched execution/);
    expect(configure).toBeDisabled();

    modalResult.resolve({
      plan: {
        ...pipeline(requested.id, 'Configured Pipeline'),
        executionId: 'rendered-execution-id',
        stages: [],
      } as any,
      config: { ...requested, name: 'Configured Pipeline' },
    });

    await waitForPipeline('Configured Pipeline');
    expect(screen.getByText(/Enriched execution/)).toBeVisible();
    expect(screen.getByRole('button', { name: /Save Changes/ })).toBeVisible();
    expect(configure).toBeEnabled();
  });

  it('applies only the latest template modal result and keeps loading until it completes', async () => {
    const requested = templatedV1();
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(originalPlan);
    const firstModal = deferred<{ plan: IPipeline; config: IPipeline }>();
    const secondModal = deferred<{ plan: IPipeline; config: IPipeline }>();
    const showModal = vi
      .spyOn(ReactModal, 'show')
      .mockReturnValueOnce(firstModal.promise)
      .mockReturnValueOnce(secondModal.promise);

    renderPage(app);
    const configure = await screen.findByRole('button', { name: /Configure Template/ });
    act(() => {
      fireEvent.click(configure);
      fireEvent.click(configure);
    });

    expect(showModal).toHaveBeenCalledTimes(2);
    expect(configure).toBeDisabled();

    firstModal.resolve({
      plan: { ...originalPlan, name: 'Stale First Plan' },
      config: { ...requested, name: 'Stale First Config' },
    });
    await flush();

    expect(screen.getByRole('heading', { name: /Template Pipeline/ })).toBeVisible();
    expect(configure).toBeDisabled();

    secondModal.resolve({
      plan: { ...originalPlan, name: 'Latest Plan' },
      config: { ...requested, name: 'Latest Config' },
    });
    await waitForPipeline('Latest Config');

    expect(configure).toBeEnabled();
    expect(screen.getByRole('button', { name: /Save Changes/ })).toBeVisible();
  });

  it('ignores a template modal result after a different pipeline load supersedes it', async () => {
    const user = setupUser();
    const requested = templatedV1();
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    const reloaded = { ...templatedV1(), id: 'reloaded-pipeline-id', name: 'Reloaded Pipeline' } as IPipeline;
    (reloaded as any).config.pipeline.name = reloaded.name;
    (reloaded as any).config.pipeline.pipelineConfigId = reloaded.id;
    const reloadedPlan = { ...pipeline(reloaded.id, reloaded.name), stages: [] } as IPipeline;
    const reloadedApp = createApp([reloaded]);
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockImplementation((config: IPipeline) =>
      Promise.resolve(config.name === reloaded.name ? reloadedPlan : originalPlan),
    );
    const modalResult = deferred<{ plan: IPipeline; config: IPipeline }>();
    vi.spyOn(ReactModal, 'show').mockReturnValue(modalResult.promise);

    const rendered = renderPage(app);
    await user.click(await screen.findByRole('button', { name: /Configure Template/ }));

    $stateParams.pipelineId = reloaded.id;
    rerenderPage(rendered, reloadedApp);
    await waitForPipeline('Reloaded Pipeline');

    modalResult.resolve({
      plan: { ...originalPlan, name: 'Stale Modal Plan' },
      config: { ...requested, name: 'Stale Modal Config' },
    });
    await flush();

    expect(screen.getByRole('heading', { name: /Reloaded Pipeline/ })).toBeVisible();
    expect(screen.queryByRole('button', { name: /Save Changes/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Configure Template/ })).toBeEnabled();
  });

  it('always marks a successful template configuration dirty and only save establishes the new baseline', async () => {
    const user = setupUser();
    const requested = templatedV1();
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(originalPlan);
    vi.spyOn(ReactModal, 'show').mockResolvedValue({ plan: cloneDeep(originalPlan), config: cloneDeep(requested) });

    renderPage(app);
    await user.click(await screen.findByRole('button', { name: /Configure Template/ }));
    const saved = await saveAndGetPipeline(user);

    expect(saved).toEqual(expect.objectContaining({ id: requested.id, name: requested.name }));
    expect(await screen.findByText('In sync with server')).toBeVisible();
    expect(screen.queryByRole('button', { name: /Revert/ })).not.toBeInTheDocument();
  });

  it('auto-opens a new static V1 template exactly once and does not reopen after dismissal', async () => {
    const requested = templatedV1(true);
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    $stateParams.new = '1';
    const modalResult = deferred<any>();
    const showModal = vi.spyOn(ReactModal, 'show').mockReturnValue(modalResult.promise);

    const rendered = renderPage(app);
    await waitFor(() => expect(showModal).toHaveBeenCalledTimes(1));

    modalResult.reject('dismissed');
    await flush();
    rerenderPage(rendered, app);
    await flush();

    expect(showModal).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('heading', { name: /Template Pipeline/ })).toBeVisible();
    expect(screen.getByRole('button', { name: /Configure Template/ })).toBeEnabled();
  });

  it('auto-opens a new V2 template exactly once', async () => {
    const requested = templatedV2(true);
    const plan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    $stateParams.new = '1';
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(plan);
    const modalResult = deferred<any>();
    const showModal = vi.spyOn(ReactModal, 'show').mockReturnValue(modalResult.promise);

    const rendered = renderPage(app);
    await waitFor(() => expect(showModal).toHaveBeenCalledTimes(1));

    modalResult.reject('dismissed');
    await flush();
    rerenderPage(rendered, app);
    await flush();

    expect(showModal).toHaveBeenCalledTimes(1);
  });

  it('does not auto-open a new dynamic V1 template', async () => {
    const requested = templatedV1(true, 'https://templates.example/{{ execution.id }}');
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    $stateParams.new = '1';
    const showModal = vi.spyOn(ReactModal, 'show').mockReturnValue(undefined);

    renderPage(app);
    await waitForPipeline('Template Pipeline');

    expect(showModal).not.toHaveBeenCalled();
  });

  it('ignores template modal completion after unmount', async () => {
    const user = setupUser();
    const requested = templatedV1();
    const originalPlan = { ...pipeline(requested.id, requested.name), stages: [] } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(originalPlan);
    const modalResult = deferred<{ plan: IPipeline; config: IPipeline }>();
    vi.spyOn(ReactModal, 'show').mockReturnValue(modalResult.promise);
    const consoleError = vi.spyOn(console, 'error').mockReturnValue(undefined);

    const rendered = renderPage(app);
    await user.click(await screen.findByRole('button', { name: /Configure Template/ }));
    rendered.unmount();

    modalResult.resolve({ plan: originalPlan, config: requested });
    await flush();

    expect(consoleError).not.toHaveBeenCalled();
  });

  it('saves the selected history revision when restoring pipeline history', async () => {
    const user = setupUser();
    const requested = pipeline('target-id', 'Current Pipeline');
    const restored = { ...pipeline('target-id', 'Restored Pipeline'), updateTs: 'old-revision' };
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    vi.spyOn(ReactModal, 'show').mockResolvedValue(restored);
    const savePipeline = vi.spyOn(PipelineConfigService, 'savePipeline').mockResolvedValue();

    renderPage(app);
    await waitForPipeline('Current Pipeline');
    await user.click(screen.getByRole('button', { name: 'Pipeline Actions' }));
    await user.click(screen.getByText('Show Revision History'));

    await waitFor(() =>
      expect(savePipeline).toHaveBeenCalledWith(expect.objectContaining({ name: 'Restored Pipeline' })),
    );
  });

  it('loads accounts once for an app, shows pending state, and ignores completion after unmount', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: '', type: '', isNew: true, requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    const accountRequest = deferred<IAccountDetails[]>();
    (AccountService.applicationAccounts as Mock).mockReturnValue(accountRequest.promise as any);

    const rendered = renderPage(app);
    await waitForPipeline('Requested Pipeline');

    expect(AccountService.applicationAccounts).toHaveBeenCalledExactlyOnceWith(app);
    expect(screen.getByText(/Loading application accounts/)).toBeVisible();
    expect(within(field('Stage Name')).getByRole('textbox')).toBeVisible();
    expect(within(field('Fail on Failed Expressions')).getByRole('checkbox')).toBeVisible();
    expect(screen.getByRole('button', { name: /Remove stage/ })).toBeVisible();
    expect(screen.getByRole('button', { name: /Edit stage as JSON/ })).toBeVisible();
    expect(screen.queryByText('Type', { selector: '.label-text' })).not.toBeInTheDocument();

    rerenderPage(rendered, app);
    await flush();
    expect(AccountService.applicationAccounts).toHaveBeenCalledTimes(1);

    rendered.unmount();
    accountRequest.resolve([account('aws')]);
    await flush();
    expect(AccountService.applicationAccounts).toHaveBeenCalledTimes(1);
  });

  it('renders an account loading error without unfiltered stage type choices', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: '', type: '', isNew: true, requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    (AccountService.applicationAccounts as Mock).mockRejectedValue(new Error('accounts failed'));
    const getConfigurableStageTypes = vi.spyOn(Registry.pipeline, 'getConfigurableStageTypes');

    renderPage(app);

    expect(await screen.findByText('Could not load application accounts: accounts failed')).toBeVisible();
    expect(within(field('Stage Name')).getByRole('textbox')).toBeVisible();
    expect(within(field('Fail on Failed Expressions')).getByRole('checkbox')).toBeVisible();
    expect(screen.getByRole('button', { name: /Remove stage/ })).toBeVisible();
    expect(screen.getByRole('button', { name: /Edit stage as JSON/ })).toBeVisible();
    expect(screen.queryByText('Type', { selector: '.label-text' })).not.toBeInTheDocument();
    expect(getConfigurableStageTypes).not.toHaveBeenCalled();
  });

  it('renders a successful empty account state without unfiltered stage type choices', async () => {
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: '', type: '', isNew: true, requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    (AccountService.applicationAccounts as Mock).mockResolvedValue([] as any);
    const getConfigurableStageTypes = vi.spyOn(Registry.pipeline, 'getConfigurableStageTypes');

    renderPage(app);

    expect(await screen.findByText('No application accounts are available.')).toBeVisible();
    expect(within(field('Stage Name')).getByRole('textbox')).toBeVisible();
    expect(within(field('Fail on Failed Expressions')).getByRole('checkbox')).toBeVisible();
    expect(screen.getByRole('button', { name: /Remove stage/ })).toBeVisible();
    expect(screen.getByRole('button', { name: /Edit stage as JSON/ })).toBeVisible();
    expect(screen.queryByText('Type', { selector: '.label-text' })).not.toBeInTheDocument();
    expect(getConfigurableStageTypes).not.toHaveBeenCalled();
  });

  it('filters an unselected base stage to ECS accounts and renders only the ECS implementation', async () => {
    const user = setupUser();
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
    (AccountService.applicationAccounts as Mock).mockResolvedValue(accounts as any);
    const getConfigurableStageTypes = vi.spyOn(Registry.pipeline, 'getConfigurableStageTypes');

    renderPage(app);
    await screen.findByText('ECS stage config');

    expect(getConfigurableStageTypes).toHaveBeenCalledWith(accounts);
    expect(field('Provider')).toHaveTextContent(/EC2 Container Service|ecs/);
    expect(screen.queryByText('AWS stage config')).not.toBeInTheDocument();
    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0]).toEqual(
      expect.objectContaining({ type: 'destroyServerGroup', cloudProvider: 'ecs', cloudProviderType: 'ecs' }),
    );
  });

  it('infers a persisted singleton provider without deleting existing stage configuration', async () => {
    const user = setupUser();
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
    (AccountService.applicationAccounts as Mock).mockResolvedValue([account('ecs')] as any);

    const rendered = renderPage(app);
    await screen.findByText('ECS stage config');

    expect(field('Provider')).toHaveTextContent(/EC2 Container Service|ecs/);
    expect(within(field('Provider')).queryByRole('combobox')).not.toBeInTheDocument();
    expect(providerRenderStates).not.toContain(false);
    expect(revision()).toBe(1);
    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[1]).toEqual({
      ...originalStage,
      type: 'customDestroyServerGroup',
      cloudProvider: 'ecs',
      cloudProviderType: 'ecs',
    } as any);

    rerenderPage(rendered, app);
    await flush();
    expect(revision()).toBe(1);
    expect(providerRenderStates).not.toContain(false);
  });

  [
    { presentField: 'cloudProvider', missingField: 'cloudProviderType' },
    { presentField: 'cloudProviderType', missingField: 'cloudProvider' },
  ].forEach(({ presentField, missingField }) => {
    it(`normalizes a persisted stage with only ${presentField} before rendering its provider implementation`, async () => {
      const user = setupUser();
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
      (AccountService.applicationAccounts as Mock).mockResolvedValue([account('aws'), account('ecs')] as any);

      renderPage(app);
      await screen.findByText('ECS stage config');

      expect(screen.queryByRole('combobox', { name: /provider/i })).not.toBeInTheDocument();
      expect(providerRenderStates).not.toContain(false);
      expect(revision()).toBe(1);
      const saved = await saveAndGetPipeline(user);
      expect(saved.stages[0].cloudProvider).toBe('ecs');
      expect(saved.stages[0].cloudProviderType).toBe('ecs');
      expect(saved.stages[0][missingField]).toBe('ecs');
    });
  });

  it('normalizes the same persisted stage again if JSON editing makes its provider fields incoherent', async () => {
    const user = setupUser();
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
    (AccountService.applicationAccounts as Mock).mockResolvedValue([account('aws'), account('ecs')] as any);
    vi.spyOn(ReactModal, 'show').mockImplementation((_component, props: { stage: IStage }) => {
      delete props.stage.cloudProviderType;
      return Promise.resolve();
    });

    renderPage(app);
    await screen.findByText('ECS stage config');
    expect(revision()).toBe(1);

    await user.click(screen.getByRole('button', { name: /Edit stage as JSON/ }));
    await waitFor(() => expect(revision()).toBe(3));

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0]).toEqual(expect.objectContaining({ cloudProvider: 'ecs', cloudProviderType: 'ecs' }));
    expect(providerRenderStates).not.toContain(false);
  });

  it('waits for provider selection, switches provider implementations, and updates once per selection', async () => {
    const user = setupUser();
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
    (AccountService.applicationAccounts as Mock).mockResolvedValue([account('aws'), account('ecs')] as any);
    const configurableStageTypes = Registry.pipeline.getConfigurableStageTypes([account('aws'), account('ecs')]);
    configurableStageTypes[0].cloudProviders.push('gcp');
    vi.spyOn(Registry.pipeline, 'getConfigurableStageTypes').mockReturnValue(configurableStageTypes);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    expect(screen.queryByText('AWS stage config')).not.toBeInTheDocument();
    expect(screen.queryByText('ECS stage config')).not.toBeInTheDocument();
    const initialRevision = revision();

    await choose(user, 'Provider', 'ecs');
    expect(await screen.findByText('ECS stage config')).toBeVisible();
    expect(screen.queryByText('AWS stage config')).not.toBeInTheDocument();
    expect(revision()).toBe(initialRevision + 1);
    const details = document.querySelector('.stage-details') as HTMLElement;
    expect(details.children[0]).toContainElement(screen.getByText('Provider', { selector: '.label-text' }));
    expect(details.children[1]).toContainElement(screen.getByText('ECS stage config'));

    await choose(user, 'Provider', 'aws');
    expect(await screen.findByText('AWS stage config')).toBeVisible();
    expect(screen.queryByText('ECS stage config')).not.toBeInTheDocument();
    expect(revision()).toBe(initialRevision + 2);

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0]).toEqual(expect.objectContaining({ cloudProvider: 'aws', cloudProviderType: 'aws' }));

    await user.click(select('Provider'));
    await user.click(screen.getByRole('option', { name: /^gcp/ }));
    expect(
      screen.getByText('No provider implementation found for stage type "destroyServerGroup" and provider "gcp".'),
    ).toBeVisible();
  });

  it('removes ECS-specific fields while preserving common controls when switching from ECS to AWS', async () => {
    const user = setupUser();
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
    (AccountService.applicationAccounts as Mock).mockResolvedValue([account('aws'), account('ecs')] as any);

    renderPage(app);
    await screen.findByText('ECS stage config');
    await choose(user, 'Provider', 'aws');
    expect(await screen.findByText('AWS stage config')).toBeVisible();

    const saved = await saveAndGetPipeline(user);
    const switchedStage = saved.stages[1];
    expectCommonFieldsPreserved(switchedStage);
    expectProviderFieldsRemoved(switchedStage, 'ecs');
    expect(switchedStage).toEqual(
      expect.objectContaining({ type: 'destroyServerGroup', cloudProvider: 'aws', cloudProviderType: 'aws' }),
    );
  });

  it('removes AWS-specific fields while preserving common controls when switching from AWS to ECS', async () => {
    const user = setupUser();
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
    (AccountService.applicationAccounts as Mock).mockResolvedValue([account('aws'), account('ecs')] as any);

    renderPage(app);
    await screen.findByText('AWS stage config');
    await choose(user, 'Provider', 'ecs');
    expect(await screen.findByText('ECS stage config')).toBeVisible();

    const saved = await saveAndGetPipeline(user);
    const switchedStage = saved.stages[1];
    expectCommonFieldsPreserved(switchedStage);
    expectProviderFieldsRemoved(switchedStage, 'aws');
    expect(switchedStage).toEqual(
      expect.objectContaining({
        type: 'customDestroyServerGroup',
        cloudProvider: 'ecs',
        cloudProviderType: 'ecs',
      }),
    );
  });

  it('retains only stage identity fields when changing stage type', async () => {
    const user = setupUser();
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
    (AccountService.applicationAccounts as Mock).mockResolvedValue([account('ecs')] as any);

    renderPage(app);
    await screen.findByText('ECS stage config');
    await choose(user, 'Type', 'Wait');

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[1]).toEqual({
      refId: '2',
      requisiteStageRefIds: ['1'],
      isNew: true,
      name: 'Custom destroy name',
      type: 'wait',
    } as any);
  });

  it('persists an implementation-specific stage key when selecting its provider', async () => {
    const user = setupUser();
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
    (AccountService.applicationAccounts as Mock).mockResolvedValue([account('ecs')] as any);

    renderPage(app);
    await screen.findByText('ECS stage config');

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0]).toEqual(
      expect.objectContaining({ type: 'customDestroyServerGroup', cloudProvider: 'ecs', cloudProviderType: 'ecs' }),
    );
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

    renderPage(app);

    expect(await screen.findByText('Regular stage config')).toBeVisible();
    expect(screen.queryByText('Provider', { selector: '.label-text' })).not.toBeInTheDocument();
  });

  it('applies cloned defaults, alias, and default label once through the dirty-state update path', async () => {
    const user = setupUser();
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

    const rendered = renderPage(app);
    await screen.findByText('Regular stage config');
    expect(revision()).toBe(1);

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0]).toEqual(
      expect.objectContaining({
        name: 'Regular With Defaults',
        alias: 'legacyRegular',
        nested: { value: 'default value' },
        credentials: 'default-account',
      }),
    );
    expect(saved.stages[0].nested).not.toBe(defaults.nested);

    rerenderPage(rendered, app);
    await flush();
    expect(revision()).toBe(1);
  });

  it('preserves strategy stage type filtering after accounts load', async () => {
    const user = setupUser();
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

    renderPage(app);
    await waitForPipeline('Requested Strategy');
    await user.click(select('Type'));

    expect((await screen.findAllByRole('option')).map((option) => option.textContent)).toEqual([
      expect.stringContaining('Strategy Stage'),
    ]);
    expect(screen.queryByRole('option', { name: /Pipeline Only/ })).not.toBeInTheDocument();
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
    const oldRequest = deferred<IAccountDetails[]>();
    const newRequest = deferred<IAccountDetails[]>();
    (AccountService.applicationAccounts as Mock).mockImplementation((requestedApp) =>
      requestedApp === oldApp ? oldRequest.promise : newRequest.promise,
    );

    const rendered = renderPage(oldApp);
    await waitForPipeline('Old Pipeline');
    rerenderPage(rendered, newApp);
    await waitForPipeline('New Pipeline');

    newRequest.resolve([account('ecs')]);
    expect(await screen.findByText('ECS stage config')).toBeVisible();
    expect(field('Provider')).toHaveTextContent(/EC2 Container Service|ecs/);

    oldRequest.resolve([account('aws')]);
    await flush();
    expect(field('Provider')).toHaveTextContent(/EC2 Container Service|ecs/);
    expect(screen.getByText('ECS stage config')).toBeVisible();
    expect(screen.queryByText('AWS stage config')).not.toBeInTheDocument();
  });

  it('marks a copied provider stage as new so its provider remains editable', async () => {
    const user = setupUser();
    registerBaseProviderStages();
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    const copiedStage = providerStage('ecs');
    copiedStage.isNew = false;
    $stateParams.pipelineId = requested.id;
    (AccountService.applicationAccounts as Mock).mockResolvedValue([account('aws'), account('ecs')] as any);
    vi.spyOn(ReactModal, 'show').mockResolvedValue(copiedStage);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await user.click(screen.getByRole('button', { name: /Copy an existing stage/ }));

    expect(await screen.findByText('ECS stage config')).toBeVisible();
    expect(select('Provider')).toBeVisible();
    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[1].isNew).toBe(true);
  });

  it('does not fall back to the first pipeline or match by name when the id is missing', async () => {
    const app = createApp([pipeline('first-id', 'missing-id')]);
    $stateParams.pipelineId = 'missing-id';

    renderPage(app);

    expect(await screen.findByText('No pipeline found with that name.')).toBeVisible();
    expect(document.querySelector('.pipeline-configurer')).not.toBeInTheDocument();
  });

  it('applies stage JSON edits to the stage editor and the pipeline', async () => {
    const user = setupUser();
    registerMountedStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Bake', type: 'mounted', value: 'before', requisiteStageRefIds: [] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    expect(mountedStageValue()).toBe('before');

    await user.click(screen.getByRole('button', { name: /Edit stage as JSON/ }));
    setJsonEditorValue((stage) => ({ ...stage, value: 'after' }));
    await user.click(screen.getByRole('button', { name: /Update Stage/ }));

    await waitFor(() => expect(mountedStageValue()).toBe('after'));
    await user.click(screen.getByRole('button', { name: /Revert/ }));
    await waitFor(() => expect(mountedStageValue()).toBe('before'));

    await user.click(screen.getByRole('button', { name: /Edit stage as JSON/ }));
    setJsonEditorValue((stage) => ({ ...stage, value: 'after' }));
    await user.click(screen.getByRole('button', { name: /Update Stage/ }));

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0].value).toBe('after');
  });

  it('mounts a new stage editor when selecting another stage of the same type', async () => {
    const user = setupUser();
    registerMountedStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'First', type: 'mounted', value: 'first', requisiteStageRefIds: [] } as any,
      { refId: '2', name: 'Second', type: 'mounted', value: 'second', requisiteStageRefIds: ['1'] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 0);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    expect(mountedStageValue()).toBe('first');

    await user.click(graphLabel('Second'));

    await waitFor(() => expect(mountedStageValue()).toBe('second'));
  });

  it('does not repeat stage type lookups when a stage field changes', async () => {
    const user = setupUser();
    registerStageTypes();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Build', type: 'wait', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await flush();

    const getConfigurableStageTypes = vi.spyOn(Registry.pipeline, 'getConfigurableStageTypes');
    const getProvidersFor = vi.spyOn(Registry.pipeline, 'getProvidersFor');
    const stageName = within(field('Stage Name')).getByRole('textbox');
    await user.clear(stageName);
    await user.type(stageName, 'Bake');

    await waitFor(() => expect(graphLabel('Bake')).toBeVisible());
    expect(getConfigurableStageTypes).not.toHaveBeenCalled();
    expect(getProvidersFor).not.toHaveBeenCalled();
  });

  it('applies pipeline JSON edits to the stage editor', async () => {
    const user = setupUser();
    registerMountedStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Bake', type: 'mounted', value: 'before', requisiteStageRefIds: [] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    await openPipelineAction(user, 'Edit as JSON');
    setJsonEditorValue((json) => ({ ...json, stages: [{ ...json.stages[0], value: 'after' }] }));
    await user.click(screen.getByRole('button', { name: /Update Pipeline/ }));

    await waitFor(() => expect(mountedStageValue()).toBe('after'));
    expect(screen.getByRole('button', { name: /Save Changes/ })).toBeVisible();
    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0].value).toBe('after');
  });

  it('shows stage editor changes in the stage and pipeline JSON views', async () => {
    const user = setupUser();
    Registry.pipeline.registerStage({
      key: 'editing',
      label: 'Editing',
      component: EditingStageConfig,
    } as IStageTypeConfig);
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Bake', type: 'editing', value: 'before', requisiteStageRefIds: [] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await user.click(screen.getByRole('button', { name: 'Edit value' }));

    await user.click(screen.getByRole('button', { name: /Edit stage as JSON/ }));
    expect(jsonEditorValue().value).toBe('edited');

    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    await openPipelineAction(user, 'Edit as JSON');
    expect(jsonEditorValue().stages[0].value).toBe('edited');
  });

  it('keeps the stage editor mounted while its stage is edited', async () => {
    const user = setupUser();
    let mounts = 0;
    const MountCountingStageConfig = (props: { updateStageField: (changes: any) => void }) => {
      React.useEffect(() => {
        mounts++;
      }, []);
      return <EditingStageConfig {...props} />;
    };
    Registry.pipeline.registerStage({
      key: 'editing',
      label: 'Editing',
      component: MountCountingStageConfig,
    } as IStageTypeConfig);
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Build', type: 'editing', requisiteStageRefIds: [] } as any,
      { refId: '2', name: 'Bake', type: 'editing', value: 'before', requisiteStageRefIds: [] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    await user.click(screen.getByRole('button', { name: 'Edit value' }));
    const stageName = within(field('Stage Name')).getByRole('textbox');
    await user.clear(stageName);
    await user.type(stageName, 'Package');
    await choose(user, 'Depends On', 'Build');

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[1]).toEqual(
      expect.objectContaining({ value: 'edited', name: 'Package', requisiteStageRefIds: ['1'] }),
    );
    expect(mounts).toBe(1);
  });

  it("resets an editor's own state when changes are reverted", async () => {
    const user = setupUser();
    // Like Formik-based editors, keeps its own copy of the stage
    const LocalStateStageConfig = ({
      stage,
      updateStageField,
    }: {
      stage: any;
      updateStageField: (changes: any) => void;
    }) => {
      const [value, setValue] = React.useState(stage.value);
      return (
        <input
          aria-label="Local value"
          value={value}
          onChange={(event) => {
            setValue(event.target.value);
            updateStageField({ value: event.target.value });
          }}
        />
      );
    };
    Registry.pipeline.registerStage({
      key: 'local',
      label: 'Local',
      component: LocalStateStageConfig,
    } as IStageTypeConfig);
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 0);
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    const localValue = screen.getByRole('textbox', { name: 'Local value' });
    await user.clear(localValue);
    await user.type(localValue, 'edited');
    expect(localValue).toHaveValue('edited');

    await user.click(screen.getByRole('button', { name: /Revert/ }));

    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Local value' })).toHaveValue('before'));
    await openPipelineAction(user, 'Edit as JSON');
    expect(jsonEditorValue().stages[0].value).toBe('before');
  });

  it("mounts the next stage's editor when the selected stage is removed", async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Bake A', type: 'local', value: 'a', requisiteStageRefIds: [] } as any,
      { refId: '2', name: 'Bake B', type: 'local', value: 'b', requisiteStageRefIds: ['1'] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 0);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    expect(localStageInput()).toHaveValue('a');

    await user.click(screen.getByRole('button', { name: /Remove stage/ }));

    await waitFor(() => expect(localStageInput()).toHaveValue('b'));
    expect(within(graph()).queryByText('Bake A', { selector: '.label-body a' })).not.toBeInTheDocument();
    expect(graphLabel('Bake B')).toBeVisible();
    const saved = await saveAndGetPipeline(user);
    expect(saved.stages.map((stage: IStage) => stage.refId)).toEqual(['2']);
  });

  it('keeps the editor and its state when the selected stage is selected again', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 0);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await typeLocalValue(user, 'edited');
    await user.click(graphLabel('Bake'));

    expect(localStageInput()).toHaveValue('edited');
    expect(localStageMounts).toBe(1);
  });

  it("keeps each stage's edits when switching between stages of the same type", async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Bake A', type: 'local', value: 'a', requisiteStageRefIds: [] } as any,
      { refId: '2', name: 'Bake B', type: 'local', value: 'b', requisiteStageRefIds: ['1'] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await typeLocalValue(user, 'b-edited');

    await user.click(graphLabel('Bake A'));
    await waitFor(() => expect(localStageInput()).toHaveValue('a'));

    await user.click(graphLabel('Bake B'));
    await waitFor(() => expect(localStageInput()).toHaveValue('b-edited'));
    const saved = await saveAndGetPipeline(user);
    expect(saved.stages.map((stage: IStage) => stage.value)).toEqual(['a', 'b-edited']);
  });

  it('shows edits after leaving the stage and coming back', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 0);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await typeLocalValue(user, 'edited');

    await user.click(graphLabel('Configuration'));
    await waitFor(() => expect(localStageInput()).not.toBeInTheDocument());

    await user.click(graphLabel('Bake'));
    await waitFor(() => expect(localStageInput()).toHaveValue('edited'));
  });

  it("mounts the new type's editor and redraws the graph when stage JSON changes the type", async () => {
    const user = setupUser();
    registerLocalStage();
    Registry.pipeline.registerStage({
      key: 'other',
      label: 'Other',
      component: () => <div className="other-stage-config">Other stage config</div>,
    } as IStageTypeConfig);
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 0);
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    await user.click(screen.getByRole('button', { name: /Edit stage as JSON/ }));
    setJsonEditorValue((stage) => ({ ...stage, type: 'other', name: 'Renamed' }));
    await user.click(screen.getByRole('button', { name: /Update Stage/ }));

    expect(await screen.findByText('Other stage config')).toBeVisible();
    expect(localStageInput()).not.toBeInTheDocument();
    await waitFor(() => expect(graphLabel('Renamed')).toBeVisible());
  });

  it('keeps the editor and the clean state when JSON edits are cancelled', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 0);
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    await user.click(screen.getByRole('button', { name: /Edit stage as JSON/ }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    await user.click(screen.getByRole('button', { name: 'Pipeline Actions' }));
    await user.click(screen.getByText('Edit as JSON'));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    await flush();

    expect(localStageMounts).toBe(1);
    expect(localStageInput()).toHaveValue('before');
    expect(screen.queryByRole('button', { name: /Save Changes/ })).not.toBeInTheDocument();
  });

  it('handles a pipeline JSON edit that removes the selected stage', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 0);
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    expect(localStageInput()).toHaveValue('before');

    await editPipelineJson(user, (json) => ({ ...json, stages: [] }));

    await waitFor(() => expect(localStageInput()).not.toBeInTheDocument());
    expect(within(graph()).queryByText('Bake', { selector: '.label-body a' })).not.toBeInTheDocument();
    expect(executionOptions()).toBeVisible();
    const saved = await saveAndGetPipeline(user);
    expect(saved.stages).toEqual([]);
  });

  it('selects the last remaining stage when a pipeline JSON edit removes the selected stage', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Bake A', type: 'local', value: 'a', requisiteStageRefIds: [] } as any,
      { refId: '2', name: 'Bake B', type: 'local', value: 'b', requisiteStageRefIds: ['1'] } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    expect(localStageInput()).toHaveValue('b');

    await editPipelineJson(user, (json) => ({ ...json, stages: json.stages.slice(0, 1) }));

    await waitFor(() => expect(localStageInput()).toHaveValue('a'));
  });

  it('shows the triggers when a restored history revision has no stages', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Bake A', type: 'local', value: 'a', requisiteStageRefIds: [] } as any,
      { refId: '2', name: 'Bake B', type: 'local', value: 'b', requisiteStageRefIds: ['1'] } as any,
    ];
    const restored = { ...pipeline('target-id', 'Requested Pipeline'), stages: [] as any[] };
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);
    vi.spyOn(ReactModal, 'show').mockResolvedValue(restored);
    vi.spyOn(PipelineConfigService, 'savePipeline').mockResolvedValue();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    expect(localStageInput()).toHaveValue('b');

    await user.click(screen.getByRole('button', { name: 'Pipeline Actions' }));
    await user.click(screen.getByText('Show Revision History'));

    await waitFor(() => expect(localStageInput()).not.toBeInTheDocument());
    expect(executionOptions()).toBeVisible();
  });

  it('selects the last remaining stage when template configuration removes the selected stage', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = templatedV1();
    const originalPlan = {
      ...pipeline(requested.id, requested.name),
      stages: [
        { refId: '1', name: 'Bake A', type: 'local', value: 'a', requisiteStageRefIds: [] } as any,
        { refId: '2', name: 'Bake B', type: 'local', value: 'b', requisiteStageRefIds: ['1'] } as any,
      ],
    } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 1);
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(originalPlan);
    const modalResult = deferred<{ plan: IPipeline; config: IPipeline }>();
    vi.spyOn(ReactModal, 'show').mockReturnValue(modalResult.promise);

    renderPage(app);
    await waitFor(() => expect(localStageInput()).toHaveValue('b'));

    await user.click(screen.getByRole('button', { name: /Configure Template/ }));
    modalResult.resolve({ plan: { ...originalPlan, stages: originalPlan.stages.slice(0, 1) }, config: requested });

    await waitFor(() => expect(localStageInput()).toHaveValue('a'));
  });

  it('gives a copied stage its own editor and values', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id, 0);
    vi.spyOn(ReactModal, 'show').mockResolvedValue({
      refId: '9',
      name: 'Bake',
      type: 'local',
      value: 'before',
      requisiteStageRefIds: [],
    });

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await typeLocalValue(user, 'original-edited');

    await user.click(screen.getByRole('button', { name: /Copy an existing stage/ }));

    await waitFor(() => expect(localStageInput()).toHaveValue('before'));
    await typeLocalValue(user, 'copy-edited');

    const saved = await saveAndGetPipeline(user);
    expect(saved.stages.map((stage: any) => stage.value)).toEqual(['original-edited', 'copy-edited']);
  });

  it('keeps the stage editor mounted through a save, and later edits still apply', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    const savePipeline = vi.spyOn(PipelineConfigService, 'savePipeline').mockResolvedValue();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await typeLocalValue(user, 'edited');
    await user.click(screen.getByRole('button', { name: /Save Changes/ }));

    await waitFor(() => expect(screen.queryByRole('button', { name: /Save Changes/ })).not.toBeInTheDocument());
    expect(savePipeline).toHaveBeenCalledWith(
      expect.objectContaining({ stages: [expect.objectContaining({ value: 'edited' })] }),
    );
    expect(localStageInput()).toHaveValue('edited');
    expect(localStageMounts).toBe(1);

    await typeLocalValue(user, 'edited again');

    expect(screen.getByRole('button', { name: /Save Changes/ })).toBeVisible();
    expect(localStageMounts).toBe(1);
    await user.click(screen.getByRole('button', { name: /Save Changes/ }));
    await waitFor(() => expect(savePipeline).toHaveBeenCalledTimes(2));
    expect(savePipeline.mock.lastCall[0].stages[0].value).toBe('edited again');
  });

  it('keeps and flags edits made while a save is in progress', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    const saveResult = deferred<void>();
    const savePipeline = vi.spyOn(PipelineConfigService, 'savePipeline').mockReturnValue(saveResult.promise);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await typeLocalValue(user, 'edited');
    await user.click(screen.getByRole('button', { name: /Save Changes/ }));
    await typeLocalValue(user, 'during save');
    await act(async () => {
      saveResult.resolve();
      await flush();
    });

    expect(savePipeline).toHaveBeenCalledWith(
      expect.objectContaining({ stages: [expect.objectContaining({ value: 'edited' })] }),
    );
    expect(localStageInput()).toHaveValue('during save');
    expect(screen.getByRole('button', { name: /Save Changes/ })).toBeVisible();
    savePipeline.mockResolvedValue();
    await user.click(screen.getByRole('button', { name: /Save Changes/ }));
    await waitFor(() => expect(savePipeline).toHaveBeenCalledTimes(2));
    expect(savePipeline.mock.lastCall[0].stages[0].value).toBe('during save');
  });

  it('keeps the new updateTs when a save finishes while the stage JSON modal is open', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = { ...pipeline('target-id', 'Requested Pipeline'), updateTs: 1 };
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    const saveResult = deferred<void>();
    // A save updates the submitted pipeline's updateTs, so record it when it is submitted
    const submittedUpdateTs: number[] = [];
    const savePipeline = vi.spyOn(PipelineConfigService, 'savePipeline').mockImplementation((toSave: IPipeline) => {
      submittedUpdateTs.push(toSave.updateTs);
      return saveResult.promise;
    });
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await typeLocalValue(user, 'edited');
    await user.click(screen.getByRole('button', { name: /Save Changes/ }));
    await user.click(screen.getByRole('button', { name: /Edit stage as JSON/ }));
    // The server stores a new updateTs while the modal is open
    requested.updateTs = 2;
    saveResult.resolve();
    await screen.findByText('In sync with server');
    setJsonEditorValue((stage) => ({ ...stage, value: 'from json' }));
    await user.click(screen.getByRole('button', { name: /Update Stage/ }));

    await user.click(await screen.findByRole('button', { name: /Save Changes/ }));
    await waitFor(() => expect(savePipeline).toHaveBeenCalledTimes(2));
    expect(submittedUpdateTs).toEqual([1, 2]);
    expect(savePipeline.mock.lastCall[0].stages).toEqual([expect.objectContaining({ value: 'from json' })]);
  });

  it('keeps the new updateTs when a save finishes while the pipeline JSON modal is open', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = { ...pipeline('target-id', 'Requested Pipeline'), updateTs: 1 };
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    const saveResult = deferred<void>();
    // A save updates the submitted pipeline's updateTs, so record it when it is submitted
    const submittedUpdateTs: number[] = [];
    const savePipeline = vi.spyOn(PipelineConfigService, 'savePipeline').mockImplementation((toSave: IPipeline) => {
      submittedUpdateTs.push(toSave.updateTs);
      return saveResult.promise;
    });
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await typeLocalValue(user, 'edited');
    await user.click(screen.getByRole('button', { name: /Save Changes/ }));
    await openPipelineAction(user, 'Edit as JSON');

    expect(jsonEditorValue().updateTs).toBe(1);

    // The server stores a new updateTs while the modal is open
    requested.updateTs = 2;
    saveResult.resolve();
    await screen.findByText('In sync with server');
    setJsonEditorValue((json) => ({ ...json, description: 'From JSON' }));
    await user.click(screen.getByRole('button', { name: /Update Pipeline/ }));

    await user.click(await screen.findByRole('button', { name: /Save Changes/ }));
    await waitFor(() => expect(savePipeline).toHaveBeenCalledTimes(2));
    expect(submittedUpdateTs).toEqual([1, 2]);
    expect(savePipeline.mock.lastCall[0].description).toBe('From JSON');
  });

  it('keeps pending changes marked unsaved after enabling the pipeline', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = { ...pipeline('target-id', 'Requested Pipeline'), disabled: true };
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    const savePipeline = vi.spyOn(PipelineConfigService, 'savePipeline').mockResolvedValue();
    renderModals();

    renderPage(app);
    const heading = await waitForPipeline('Requested Pipeline');
    expect(heading).toHaveTextContent('(disabled)');
    await typeLocalValue(user, 'edited');
    await openPipelineAction(user, 'Enable');
    await user.click(screen.getByRole('button', { name: 'Enable pipeline' }));

    await waitFor(() => expect(heading).not.toHaveTextContent('(disabled)'));
    expect(savePipeline).toHaveBeenCalledWith(
      expect.objectContaining({ disabled: false, stages: [expect.objectContaining({ value: 'before' })] }),
    );
    expect(screen.getByRole('textbox', { name: 'Local value' })).toHaveValue('edited');
    expect(screen.getByRole('button', { name: /Save Changes/ })).toBeVisible();

    await user.click(screen.getByRole('button', { name: /Save Changes/ }));
    await waitFor(() => expect(savePipeline).toHaveBeenCalledTimes(2));
    expect(savePipeline.mock.lastCall[0]).toEqual(
      expect.objectContaining({ disabled: false, stages: [expect.objectContaining({ value: 'edited' })] }),
    );
  });

  it('saves without a stale updateTs after the pipeline is locked and unlocked', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = { ...pipeline('target-id', 'Requested Pipeline'), updateTs: 1 };
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    const { savePipeline, staleSaves, submittedUpdateTs } = staleCheckingSave(requested);
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    await openPipelineAction(user, 'Lock');
    await user.click(screen.getByRole('button', { name: 'Lock pipeline' }));
    await screen.findByText('Pipeline is locked');
    await openPipelineAction(user, 'Unlock');
    await user.click(screen.getByRole('button', { name: 'Unlock pipeline' }));
    await screen.findByText('In sync with server');
    await typeLocalValue(user, 'edited');
    await user.click(screen.getByRole('button', { name: /Save Changes/ }));

    await waitFor(() => expect(savePipeline).toHaveBeenCalledTimes(3));
    await screen.findByText('In sync with server');
    expect(staleSaves).toEqual([]);
    expect(submittedUpdateTs).toEqual([1, 2, 3]);
    expect(savePipeline.mock.lastCall[0]).toEqual(
      expect.objectContaining({ stages: [expect.objectContaining({ value: 'edited' })] }),
    );
    expect(screen.queryByRole('button', { name: /Save Changes/ })).not.toBeInTheDocument();
  });

  it('saves without a stale updateTs after the pipeline is disabled and enabled', async () => {
    const user = setupUser();
    registerLocalStage();
    const requested = { ...pipeline('target-id', 'Requested Pipeline'), updateTs: 1 };
    requested.stages = [{ refId: '1', name: 'Bake', type: 'local', value: 'before', requisiteStageRefIds: [] } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    const { savePipeline, staleSaves, submittedUpdateTs } = staleCheckingSave(requested);
    renderModals();

    renderPage(app);
    const heading = await waitForPipeline('Requested Pipeline');
    await openPipelineAction(user, 'Disable');
    await user.click(screen.getByRole('button', { name: 'Disable pipeline' }));
    await waitFor(() => expect(heading).toHaveTextContent('(disabled)'));
    await typeLocalValue(user, 'disabled edit');
    await user.click(screen.getByRole('button', { name: /Save Changes/ }));
    await waitFor(() => expect(savePipeline).toHaveBeenCalledTimes(2));
    await screen.findByText('In sync with server');
    await openPipelineAction(user, 'Enable');
    await user.click(screen.getByRole('button', { name: 'Enable pipeline' }));
    await waitFor(() => expect(heading).not.toHaveTextContent('(disabled)'));
    await typeLocalValue(user, 'enabled edit');
    await user.click(screen.getByRole('button', { name: /Save Changes/ }));

    await waitFor(() => expect(savePipeline).toHaveBeenCalledTimes(4));
    await screen.findByText('In sync with server');
    expect(staleSaves).toEqual([]);
    expect(submittedUpdateTs).toEqual([1, 2, 3, 4]);
    expect(savePipeline.mock.lastCall[0]).toEqual(
      expect.objectContaining({ disabled: false, stages: [expect.objectContaining({ value: 'enabled edit' })] }),
    );
    expect(screen.queryByRole('button', { name: /Save Changes/ })).not.toBeInTheDocument();
  });

  it('keeps an execution option change after more edits in a Formik stage form', async () => {
    const user = setupUser();
    Registry.pipeline.registerStage({
      label: 'Script',
      key: 'script',
      defaults: { waitForCompletion: true, failPipeline: true },
      component: ScriptStageConfig,
      validateFn: validateScriptStage,
    } as IStageTypeConfig);
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      {
        refId: '1',
        name: 'Script',
        type: 'script',
        command: 'echo 1',
        failPipeline: true,
        continuePipeline: false,
        requisiteStageRefIds: [],
      } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    const command = screen.getByDisplayValue('echo 1');
    const ignoreFailure = within(field('If stage fails')).getByRole('radio', { name: /ignore the failure/ });

    // The first edit gives the form its own copy of the stage
    fireEvent.change(command, { target: { value: 'echo 2' } });
    await user.click(ignoreFailure);
    fireEvent.change(command, { target: { value: 'echo 3' } });

    await waitFor(() => expect(ignoreFailure).toBeChecked());
    const saved = await saveAndGetPipeline(user);
    expect(saved.stages[0]).toEqual(
      expect.objectContaining({ command: 'echo 3', failPipeline: false, continuePipeline: true }),
    );
  });

  it('remounts the stage editor with a restored history revision', async () => {
    const user = setupUser();
    registerMountedStage();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.stages = [
      { refId: '1', name: 'Bake', type: 'mounted', value: 'before', requisiteStageRefIds: [] } as any,
    ];
    const restored = {
      ...pipeline('target-id', 'Requested Pipeline'),
      stages: [{ refId: '1', name: 'Bake', type: 'mounted', value: 'restored', requisiteStageRefIds: [] } as any],
    };
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    showStageConfig(requested.id);
    vi.spyOn(ReactModal, 'show').mockResolvedValue(restored);
    vi.spyOn(PipelineConfigService, 'savePipeline').mockResolvedValue();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    expect(mountedStageValue()).toBe('before');

    await openPipelineAction(user, 'Show Revision History');

    await waitFor(() => expect(mountedStageValue()).toBe('restored'));
  });

  it('applies pipeline JSON edits to the execution options', async () => {
    const user = setupUser();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.limitConcurrent = false;
    requested.maxConcurrentExecutions = 2;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    expect(maxConcurrentInput()).toHaveValue(2);

    await openPipelineAction(user, 'Edit as JSON');
    setJsonEditorValue((json) => ({ ...json, maxConcurrentExecutions: 5 }));
    await user.click(screen.getByRole('button', { name: /Update Pipeline/ }));

    await waitFor(() => expect(maxConcurrentInput()).toHaveValue(5));
  });

  it('updates the execution options when the template is configured', async () => {
    const user = setupUser();
    const requested = templatedV1();
    const originalPlan = {
      ...pipeline(requested.id, requested.name),
      stages: [],
      limitConcurrent: false,
      maxConcurrentExecutions: 2,
    } as IPipeline;
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(originalPlan);
    const modalResult = deferred<{ plan: IPipeline; config: IPipeline }>();
    vi.spyOn(ReactModal, 'show').mockReturnValue(modalResult.promise);

    renderPage(app);
    const configure = await screen.findByRole('button', { name: /Configure Template/ });
    expect(maxConcurrentInput()).toHaveValue(2);

    await user.click(configure);
    modalResult.resolve({ plan: { ...originalPlan, maxConcurrentExecutions: 5 }, config: requested });

    await waitFor(() => expect(maxConcurrentInput()).toHaveValue(5));
  });

  it('keeps a trigger form mounted while triggers and other pipeline fields are edited', async () => {
    const user = setupUser();
    const testTrigger = registerTestTrigger();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.triggers = [{ type: 'webhook', enabled: true, value: 'first' } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    const [triggerValue] = testTriggerInputs();
    await user.clear(triggerValue);
    await user.type(triggerValue, 'edited');
    await user.type(screen.getByPlaceholderText(/explain the purpose of this pipeline/), 'Described');

    expect(testTriggerValues()).toEqual(['edited']);
    expect(testTrigger.count).toBe(1);
    const saved = await saveAndGetPipeline(user);
    expect(saved.triggers[0]).toEqual(expect.objectContaining({ value: 'edited' }));
    expect(saved.description).toBe('Described');
  });

  it('keeps the other trigger forms mounted when a trigger is removed', async () => {
    const user = setupUser();
    const testTrigger = registerTestTrigger();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.triggers = [
      { type: 'webhook', enabled: true, value: 'first' } as any,
      { type: 'webhook', enabled: true, value: 'second' } as any,
    ];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;

    renderPage(app);
    await waitForPipeline('Requested Pipeline');
    expect(testTrigger.count).toBe(2);

    await user.click(screen.getAllByRole('button', { name: 'Remove trigger' })[0]);

    await waitFor(() => expect(testTriggerValues()).toEqual(['second']));
    expect(testTrigger.count).toBe(2);
    const saved = await saveAndGetPipeline(user);
    expect(saved.triggers.map((trigger: any) => trigger.value)).toEqual(['second']);
  });

  it('resets trigger forms when the pipeline is edited as JSON or reverted', async () => {
    const user = setupUser();
    registerTestTrigger();
    const requested = pipeline('target-id', 'Requested Pipeline');
    requested.triggers = [{ type: 'webhook', enabled: true, value: 'first' } as any];
    const app = createApp([requested]);
    $stateParams.pipelineId = requested.id;
    renderModals();

    renderPage(app);
    await waitForPipeline('Requested Pipeline');

    const [triggerValue] = testTriggerInputs();
    await user.clear(triggerValue);
    await user.type(triggerValue, 'edited');
    await openPipelineAction(user, 'Edit as JSON');
    expect(jsonEditorValue().triggers[0].value).toBe('edited');
    setJsonEditorValue((json) => ({ ...json, triggers: [{ ...json.triggers[0], value: 'from json' }] }));
    await user.click(screen.getByRole('button', { name: /Update Pipeline/ }));

    await waitFor(() => expect(testTriggerValues()).toEqual(['from json']));

    await user.click(screen.getByRole('button', { name: /Revert/ }));

    await waitFor(() => expect(testTriggerValues()).toEqual(['first']));
    await openPipelineAction(user, 'Edit as JSON');
    expect(jsonEditorValue().triggers[0]).toEqual(expect.objectContaining({ value: 'first' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
  });
});
