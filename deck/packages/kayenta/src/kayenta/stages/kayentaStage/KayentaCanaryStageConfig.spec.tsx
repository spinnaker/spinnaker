import type { ReactWrapper } from 'enzyme';
import { mount } from 'enzyme';
import type { ICanaryConfig, IKayentaAccount, IKayentaStage } from '../../domain';
import { KayentaAccountType, KayentaAnalysisType } from '../../domain';
import React from 'react';
import { act } from 'react-dom/test-utils';

import type { DeckRuntimeServices } from '@spinnaker/core';
import { AccountService, CloudProviderRegistry, DeckRuntimeContext, ProviderSelectionService } from '@spinnaker/core';

import { mockHttpClient } from '../../../../../core/src/api/mock/jasmine';
import { KayentaCanaryStageConfig } from './KayentaCanaryStageConfig';

const cloneServerGroupModal = {
  show: jasmine.createSpy('showCloneServerGroupModal').and.callFake((props) => Promise.resolve(props.command)),
};
const mockRuntimeServices = ({
  serverGroupCommandBuilder: {
    buildNewServerGroupCommandForPipeline: jasmine
      .createSpy('buildNewServerGroupCommandForPipeline')
      .and.resolveTo({ viewState: {}, strategy: 'redblack' }),
    buildServerGroupCommandFromPipeline: jasmine
      .createSpy('buildServerGroupCommandFromPipeline')
      .and.resolveTo({ viewState: {}, strategy: 'redblack' }),
  },
  serverGroupTransformer: {
    convertServerGroupCommandToDeployConfiguration: jasmine
      .createSpy('convertServerGroupCommandToDeployConfiguration')
      .and.returnValue({ application: 'spinnaker', freeFormDetails: '' }),
  },
} as any) as DeckRuntimeServices;

describe('<KayentaCanaryStageConfig />', () => {
  const canaryConfig: ICanaryConfig = {
    id: 'config-1',
    name: 'Config One',
    applications: ['spinnaker'],
    metrics: [
      { name: 'Metric One', query: { type: 'prometheus' }, scopeName: 'default' } as any,
      { name: 'Metric Two', query: { type: 'prometheus' }, scopeName: 'extra' } as any,
    ],
  } as any;

  const kayentaAccounts: IKayentaAccount[] = [
    {
      name: 'metrics-account',
      supportedTypes: [KayentaAccountType.MetricsStore],
      locations: ['us-east-1'],
      recommendedLocations: ['us-east-1'],
    } as any,
    {
      name: 'storage-account',
      supportedTypes: [KayentaAccountType.ObjectStore],
      locations: [],
      recommendedLocations: [],
    } as any,
  ];

  const application = {
    name: 'spinnaker',
    ready: jasmine.createSpy('ready').and.resolveTo(),
    getDataSource: jasmine
      .createSpy('getDataSource')
      .and.returnValue({ data: [{ id: 'config-1', name: 'Config One' }] }),
    serverGroups: { loaded: true, data: [] as any[] },
  };

  let http: ReturnType<typeof mockHttpClient>;
  let mountedWrappers: ReactWrapper[];

  const defaultStage = (): IKayentaStage =>
    ({
      isNew: false,
      analysisType: KayentaAnalysisType.RealTime,
      canaryConfig: {
        canaryConfigId: 'config-1',
        canaryAnalysisIntervalMins: '5',
        lifetimeDuration: 'PT1H',
        beginCanaryAnalysisAfterMins: '0',
        scoreThresholds: { marginal: '75', pass: '95' },
        scopes: [
          {
            scopeName: 'default',
            controlScope: 'baseline-scope',
            experimentScope: 'canary-scope',
            controlLocation: 'us-east-1',
            experimentLocation: 'us-east-1',
            extendedScopeParams: {},
          },
        ],
      },
    } as any);

  beforeEach(() => {
    mountedWrappers = [];
    application.ready.calls.reset();
    application.getDataSource.calls.reset();
    http = mockHttpClient({ autoFlush: true });
    spyOn(AccountService, 'listProviders').and.resolveTo(['aws', 'gce']);
    spyOn(AccountService, 'listAccounts').and.resolveTo([{ name: 'prod', environment: 'prod' }] as any);
    spyOn(AccountService, 'challengeDestructiveActions').and.resolveTo(false);
    cloneServerGroupModal.show.calls.reset();
    (mockRuntimeServices.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline as jasmine.Spy).calls.reset();
    (mockRuntimeServices.serverGroupCommandBuilder.buildServerGroupCommandFromPipeline as jasmine.Spy).calls.reset();
    (mockRuntimeServices.serverGroupTransformer
      .convertServerGroupCommandToDeployConfiguration as jasmine.Spy).calls.reset();
  });

  afterEach(() => mountedWrappers.forEach((wrapper) => wrapper.unmount()));

  async function render(
    stage: IKayentaStage = defaultStage(),
    updateStage = jasmine.createSpy('updateStage'),
    app: typeof application = application,
    expectRequests = true,
  ): Promise<ReactWrapper> {
    if (expectRequests) {
      expectBackingData(stage);
    }
    let wrapper: ReactWrapper;
    await act(async () => {
      wrapper = mount(
        <DeckRuntimeContext.Provider value={{ services: mockRuntimeServices }}>
          {React.createElement(KayentaCanaryStageConfig as React.ComponentType<any>, {
            application: app,
            stage,
            updateStage,
          })}
        </DeckRuntimeContext.Provider>,
      );
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    wrapper.update();
    mountedWrappers.push(wrapper);
    return wrapper;
  }

  it('renders loading without throwing for a bare new stage', () => {
    const pendingApplication = {
      ...application,
      ready: jasmine.createSpy('ready').and.returnValue(new Promise(() => undefined)),
    };
    const wrapper = mount(
      <DeckRuntimeContext.Provider value={{ services: mockRuntimeServices }}>
        {React.createElement(KayentaCanaryStageConfig as React.ComponentType<any>, {
          application: pendingApplication,
          stage: { isNew: true },
          updateStage: jasmine.createSpy('updateStage'),
        })}
      </DeckRuntimeContext.Provider>,
    );

    expect(wrapper.text()).toContain('Loading');
    wrapper.unmount();
  });

  it('initializes a bare new stage after backing data loads', async () => {
    const stage = { isNew: true } as any;
    const updateStage = jasmine.createSpy('updateStage');

    const wrapper = await render(stage, updateStage);

    expect(wrapper.text()).toContain('Analysis Type');
    expect(stage.analysisType).toBe(KayentaAnalysisType.RealTimeAutomatic);
    expect(stage.canaryConfig.scoreThresholds).toEqual({ marginal: null, pass: null });
    expect(stage.canaryConfig.scopes).toEqual([{ scopeName: 'default' }]);
    expect(updateStage).toHaveBeenCalledWith(jasmine.objectContaining({ canaryConfig: stage.canaryConfig }));
  });

  it('loads account options for a bare new stage with one supported provider', async () => {
    (AccountService.listProviders as jasmine.Spy).and.resolveTo(['aws']);
    const stage = { isNew: true } as any;

    const wrapper = await render(stage);

    expect(wrapper.text()).not.toContain('Provider');
    expect(AccountService.listAccounts).toHaveBeenCalledOnceWith('aws');
    expect(wrapper.find('option[value="prod"]')).toHaveSize(1);

    wrapper
      .find('select')
      .filterWhere((select) => select.find('option[value="prod"]').length === 1)
      .simulate('change', {
        target: { value: 'prod' },
      });

    expect(stage.deployments.baseline.account).toBe('prod');
  });

  it('reverts the provider and keeps prior account options when provider account loading fails', async () => {
    const stage = defaultStage();
    stage.isNew = true;
    stage.analysisType = KayentaAnalysisType.RealTimeAutomatic;
    const updateStage = jasmine.createSpy('updateStage');
    const wrapper = await render(stage, updateStage);
    updateStage.calls.reset();
    (AccountService.listAccounts as jasmine.Spy).and.rejectWith(new Error('accounts failed'));

    wrapper
      .find('select')
      .filterWhere((select) => select.find('option[value="gce"]').length === 1)
      .simulate('change', {
        target: { value: 'gce' },
      });
    await act(async () => Promise.resolve());
    wrapper.update();

    expect(stage.deployments.baseline.cloudProvider).toBe('aws');
    expect(stage.deployments.baseline.account).toBeNull();
    expect(stage.deployments.baseline.cluster).toBeNull();
    expect(updateStage).toHaveBeenCalledWith(jasmine.objectContaining({ deployments: stage.deployments }));
    expect(wrapper.find('option[value="prod"]')).toHaveSize(1);
  });

  it('renders a loading spinner while backing data loads', () => {
    const pendingApplication = {
      ...application,
      ready: jasmine.createSpy('ready').and.returnValue(new Promise(() => undefined)),
    };
    const wrapper = mount(
      <DeckRuntimeContext.Provider value={{ services: mockRuntimeServices }}>
        {React.createElement(KayentaCanaryStageConfig as React.ComponentType<any>, {
          application: pendingApplication,
          stage: defaultStage(),
          updateStage: jasmine.createSpy('updateStage'),
        })}
      </DeckRuntimeContext.Provider>,
    );

    expect(wrapper.text()).toContain('Loading');
    wrapper.unmount();
  });

  it('renders the main stage config sections after loading', async () => {
    const wrapper = await render();

    expect(wrapper.text()).toContain('Analysis Type');
    expect(wrapper.text()).toContain('Config Name');
    expect(wrapper.text()).toContain('Lifetime');
    expect(wrapper.text()).toContain('Interval');
    expect(wrapper.text()).toContain('Baseline + Canary Pair');
    expect(wrapper.text()).toContain('Metric Scope');
    expect(wrapper.text()).toContain('Scoring Thresholds');
    expect(wrapper.text()).toContain('Advanced Settings');
  });

  it('does not reload backing data when the stage object changes with the same refId', async () => {
    const stage = { ...defaultStage(), refId: '1' } as IKayentaStage;
    const wrapper = await render(stage);

    await act(async () => {
      wrapper.setProps({
        children: React.createElement(KayentaCanaryStageConfig, {
          application: application as any,
          stage: { ...stage, refId: '1' },
          updateStage: jasmine.createSpy('replacementUpdateStage'),
        }),
      });
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    wrapper.update();

    expect(application.ready).toHaveBeenCalledTimes(1);
  });

  it('updates the stage analysis type and calls updateStage', async () => {
    const stage = defaultStage();
    const updateStage = jasmine.createSpy('updateStage');
    const wrapper = await render(stage, updateStage);

    wrapper.find('input[name="analysisType"]').at(2).simulate('change');

    expect(stage.analysisType).toBe(KayentaAnalysisType.Retrospective);
    expect(updateStage).toHaveBeenCalledWith(
      jasmine.objectContaining({ analysisType: KayentaAnalysisType.Retrospective }),
    );
  });

  it('updates score thresholds and calls updateStage', async () => {
    const stage = defaultStage();
    const updateStage = jasmine.createSpy('updateStage');
    const wrapper = await render(stage, updateStage);

    wrapper
      .find('input[type="number"]')
      .at(3)
      .simulate('change', { target: { value: '80' } });

    expect(stage.canaryConfig.scoreThresholds.marginal).toBe('80');
    expect(updateStage).toHaveBeenCalledWith(jasmine.objectContaining({ canaryConfig: stage.canaryConfig }));
  });

  it('reverts config selection when selected config details fail to load', async () => {
    const stage = defaultStage();
    const updateStage = jasmine.createSpy('updateStage');
    const wrapper = await render(stage, updateStage);
    http.expectGET('/v2/canaryConfig/missing-config').respond(500);

    wrapper
      .find('select')
      .filterWhere((select) => select.find('option[value="config-1"]').length === 1)
      .simulate('change', {
        target: { value: 'missing-config' },
      });
    await act(async () => Promise.resolve());
    wrapper.update();

    expect(stage.canaryConfig.canaryConfigId).toBe('config-1');
    expect(updateStage.calls.mostRecent().args).toEqual([
      jasmine.objectContaining({ canaryConfig: stage.canaryConfig }),
    ]);
  });

  it('renders expression-valued lookback as static JSON editor guidance', async () => {
    const stage = defaultStage();
    (stage.canaryConfig as any).lookbackMins = '${ parameters.lookbackMins }';

    const wrapper = await render(stage);

    expect(wrapper.text()).toContain(
      'Using a sliding lookback duration defined by an expression viewable in the pipeline JSON editor.',
    );
    expect(wrapper.find('[data-test-id="lookback-minutes-input"]')).toHaveSize(0);
  });

  it('shows retrospective start and end fields only for retrospective analysis', async () => {
    const realTime = await render(defaultStage());
    expect(realTime.text()).not.toContain('Start Time');
    expect(realTime.text()).not.toContain('End Time');

    const retrospectiveStage = defaultStage();
    retrospectiveStage.analysisType = KayentaAnalysisType.Retrospective;
    const retrospective = await render(retrospectiveStage);
    expect(retrospective.text()).toContain('Start Time');
    expect(retrospective.text()).toContain('End Time');
  });

  it('shows real-time automatic baseline selectors only for real-time automatic analysis', async () => {
    const realTime = await render(defaultStage());
    expect(realTime.text()).not.toContain('Baseline Version');

    const automaticStage = defaultStage();
    automaticStage.isNew = true;
    automaticStage.analysisType = KayentaAnalysisType.RealTimeAutomatic;
    const automatic = await render(automaticStage);
    expect(automatic.text()).toContain('Baseline Version');
    expect(automatic.text()).toContain('Provider');
    expect(automatic.text()).toContain('Account');
    expect(automatic.text()).toContain('Cluster');
  });

  it('uses core provider services for server group pair add and edit actions', async () => {
    const getProviderConfig = spyOn(CloudProviderRegistry, 'getValue').and.returnValue({
      CloneServerGroupModal: cloneServerGroupModal,
    });
    const selectProvider = spyOn(ProviderSelectionService, 'selectProvider').and.resolveTo('aws');
    const control = { cloudProvider: 'aws', account: 'prod', location: 'us-east-1', application: 'api' };
    const stage = defaultStage();
    stage.analysisType = KayentaAnalysisType.RealTimeAutomatic;
    stage.deployments = {
      baseline: { cloudProvider: 'aws', application: 'spinnaker' },
      serverGroupPairs: [
        {
          control,
          experiment: { cloudProvider: 'aws', account: 'prod', location: 'us-east-1', application: 'api' },
        },
      ],
    } as any;
    const wrapper = await render(stage);

    wrapper
      .find('a')
      .filterWhere((link) => link.text() === 'Edit')
      .first()
      .simulate('click');
    await act(async () => Promise.resolve());

    expect(getProviderConfig).toHaveBeenCalledWith('aws', 'serverGroup');
    const buildEditCommand = mockRuntimeServices.serverGroupCommandBuilder
      .buildServerGroupCommandFromPipeline as jasmine.Spy;
    expect(buildEditCommand).toHaveBeenCalledTimes(1);
    expect(buildEditCommand.calls.argsFor(0)[0]).toBe(application);
    expect(buildEditCommand.calls.argsFor(0)[1]).toBe(control);
    expect(buildEditCommand.calls.argsFor(0).slice(2)).toEqual([null, null]);

    const emptyStage = defaultStage();
    emptyStage.analysisType = KayentaAnalysisType.RealTimeAutomatic;
    emptyStage.deployments = {
      baseline: { application: 'spinnaker' },
      serverGroupPairs: [],
    } as any;
    const emptyWrapper = await render(emptyStage);

    emptyWrapper.find('button.add-new').first().simulate('click');
    await act(async () => Promise.resolve());

    expect(selectProvider).toHaveBeenCalledWith(application, 'serverGroup', jasmine.any(Function));
    expect(mockRuntimeServices.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline).toHaveBeenCalledWith(
      'aws',
      null,
      null,
    );
    expect(cloneServerGroupModal.show).toHaveBeenCalled();
  });

  function expectBackingData(stage: IKayentaStage): void {
    if (stage.canaryConfig?.canaryConfigId) {
      http.expectGET(`/v2/canaryConfig/${stage.canaryConfig.canaryConfigId}`).respond(200, canaryConfig);
    }
    http.expectGET('/v2/canaries/credentials').respond(200, kayentaAccounts);
  }
});
