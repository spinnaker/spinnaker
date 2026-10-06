import { fireEvent, render as rtlRender, waitFor, within } from '@testing-library/react';
import React from 'react';
import type { Mock } from 'vitest';

import type { DeckRuntimeServices } from '@spinnaker/core';
import { AccountService, CloudProviderRegistry, DeckRuntimeContext, ProviderSelectionService } from '@spinnaker/core';

import { KayentaCanaryStageConfig } from './KayentaCanaryStageConfig';
import { mockHttpClient } from '../../../../../core/src/api/mock/mockHttpSupport';
import type { ICanaryConfig, IKayentaAccount, IKayentaStage } from '../../domain';
import { KayentaAccountType, KayentaAnalysisType } from '../../domain';

const cloneServerGroupModal = {
  show: vi.fn().mockImplementation((props) => Promise.resolve(props.command)),
};
const mockRuntimeServices = ({
  serverGroupCommandBuilder: {
    buildNewServerGroupCommandForPipeline: vi.fn().mockResolvedValue({ viewState: {}, strategy: 'redblack' }),
    buildServerGroupCommandFromPipeline: vi.fn().mockResolvedValue({ viewState: {}, strategy: 'redblack' }),
  },
  serverGroupTransformer: {
    convertServerGroupCommandToDeployConfiguration: vi
      .fn()
      .mockReturnValue({ application: 'spinnaker', freeFormDetails: '' }),
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
    ready: vi.fn().mockResolvedValue(),
    getDataSource: vi.fn().mockReturnValue({ data: [{ id: 'config-1', name: 'Config One' }] }),
    serverGroups: { loaded: true, data: [] as any[] },
  };

  let http: ReturnType<typeof mockHttpClient>;

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
    application.ready.mockClear();
    application.getDataSource.mockClear();
    http = mockHttpClient({ autoFlush: true });
    vi.spyOn(AccountService, 'listProviders').mockResolvedValue(['aws', 'gce']);
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([{ name: 'prod', environment: 'prod' }] as any);
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(false);
    cloneServerGroupModal.show.mockClear();
    (mockRuntimeServices.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline as Mock).mockClear();
    (mockRuntimeServices.serverGroupCommandBuilder.buildServerGroupCommandFromPipeline as Mock).mockClear();
    (mockRuntimeServices.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration as Mock).mockClear();
  });

  const stageElement = (stage: IKayentaStage, updateStage: Mock, app: typeof application) => (
    <DeckRuntimeContext.Provider value={{ services: mockRuntimeServices }}>
      {React.createElement(KayentaCanaryStageConfig as React.ComponentType<any>, {
        application: app,
        stage,
        updateStage,
      })}
    </DeckRuntimeContext.Provider>
  );

  async function renderStage(
    stage: IKayentaStage = defaultStage(),
    updateStage: Mock = vi.fn(),
    app: typeof application = application,
    expectRequests = true,
  ) {
    if (expectRequests) {
      expectBackingData(stage);
    }
    const result = rtlRender(stageElement(stage, updateStage, app));
    await waitFor(() => expect(result.container).not.toHaveTextContent('Loading'));
    return result;
  }

  it('renders loading without throwing for a bare new stage', () => {
    const pendingApplication = {
      ...application,
      ready: vi.fn().mockReturnValue(new Promise(() => undefined)),
    };
    const { container } = rtlRender(stageElement({ isNew: true } as IKayentaStage, vi.fn(), pendingApplication));

    expect(container).toHaveTextContent('Loading');
  });

  it('initializes a bare new stage after backing data loads', async () => {
    const stage = { isNew: true } as any;
    const updateStage = vi.fn();

    const { container } = await renderStage(stage, updateStage);

    expect(container).toHaveTextContent('Analysis Type');
    expect(stage.analysisType).toBe(KayentaAnalysisType.RealTimeAutomatic);
    expect(stage.canaryConfig.scoreThresholds).toEqual({ marginal: null, pass: null });
    expect(stage.canaryConfig.scopes).toEqual([{ scopeName: 'default' }]);
    expect(updateStage).toHaveBeenCalledWith(expect.objectContaining({ canaryConfig: stage.canaryConfig }));
  });

  it('loads account options for a bare new stage with one supported provider', async () => {
    (AccountService.listProviders as Mock).mockResolvedValue(['aws']);
    const stage = { isNew: true } as any;

    const { container } = await renderStage(stage);

    expect(container).not.toHaveTextContent('Provider');
    expect(AccountService.listAccounts).toHaveBeenCalledExactlyOnceWith('aws');
    const accountOption = within(container).getByRole('option', { name: 'prod' });

    fireEvent.change(accountOption.closest('select') as HTMLSelectElement, { target: { value: 'prod' } });

    expect(stage.deployments.baseline.account).toBe('prod');
  });

  it('reverts the provider and keeps prior account options when provider account loading fails', async () => {
    const stage = defaultStage();
    stage.isNew = true;
    stage.analysisType = KayentaAnalysisType.RealTimeAutomatic;
    const updateStage = vi.fn();
    const { container } = await renderStage(stage, updateStage);
    updateStage.mockClear();
    (AccountService.listAccounts as Mock).mockRejectedValue(new Error('accounts failed'));

    const providerOption = within(container).getByRole('option', { name: 'gce' });
    fireEvent.change(providerOption.closest('select') as HTMLSelectElement, { target: { value: 'gce' } });

    await waitFor(() => expect(stage.deployments.baseline.cloudProvider).toBe('aws'));
    expect(stage.deployments.baseline.account).toBeNull();
    expect(stage.deployments.baseline.cluster).toBeNull();
    expect(updateStage).toHaveBeenCalledWith(expect.objectContaining({ deployments: stage.deployments }));
    expect(within(container).getByRole('option', { name: 'prod' })).toBeVisible();
  });

  it('renders a loading spinner while backing data loads', () => {
    const pendingApplication = {
      ...application,
      ready: vi.fn().mockReturnValue(new Promise(() => undefined)),
    };
    const { container } = rtlRender(stageElement(defaultStage(), vi.fn(), pendingApplication));

    expect(container).toHaveTextContent('Loading');
  });

  it('renders the main stage config sections after loading', async () => {
    const { container } = await renderStage();

    expect(container).toHaveTextContent('Analysis Type');
    expect(container).toHaveTextContent('Config Name');
    expect(container).toHaveTextContent('Lifetime');
    expect(container).toHaveTextContent('Interval');
    expect(container).toHaveTextContent('Baseline + Canary Pair');
    expect(container).toHaveTextContent('Metric Scope');
    expect(container).toHaveTextContent('Scoring Thresholds');
    expect(container).toHaveTextContent('Advanced Settings');
  });

  it('does not reload backing data when the stage object changes with the same refId', async () => {
    const stage = { ...defaultStage(), refId: '1' } as IKayentaStage;
    const { rerender } = await renderStage(stage);

    rerender(stageElement({ ...stage, refId: '1' }, vi.fn(), application));
    await waitFor(() => expect(application.ready).toHaveBeenCalledTimes(1));

    expect(application.ready).toHaveBeenCalledTimes(1);
  });

  it('updates the stage analysis type and calls updateStage', async () => {
    const stage = defaultStage();
    const updateStage = vi.fn();
    const { container } = await renderStage(stage, updateStage);

    fireEvent.click(within(container).getByRole('radio', { name: 'Retrospective' }));

    expect(stage.analysisType).toBe(KayentaAnalysisType.Retrospective);
    expect(updateStage).toHaveBeenCalledWith(
      expect.objectContaining({ analysisType: KayentaAnalysisType.Retrospective }),
    );
  });

  it('updates score thresholds and calls updateStage', async () => {
    const stage = defaultStage();
    const updateStage = vi.fn();
    const { container } = await renderStage(stage, updateStage);

    fireEvent.change(within(container).getAllByRole('spinbutton')[3], { target: { value: '80' } });

    expect(stage.canaryConfig.scoreThresholds.marginal).toBe('80');
    expect(updateStage).toHaveBeenCalledWith(expect.objectContaining({ canaryConfig: stage.canaryConfig }));
  });

  it('reverts config selection when selected config details fail to load', async () => {
    const stage = defaultStage();
    const updateStage = vi.fn();
    application.getDataSource.mockReturnValueOnce({
      data: [
        { id: 'config-1', name: 'Config One' },
        { id: 'missing-config', name: 'Missing Config' },
      ],
    });
    const { container } = await renderStage(stage, updateStage);
    http.expectGET('/v2/canaryConfig/missing-config').respond(500);

    const configOption = within(container).getByRole('option', { name: 'Missing Config' });
    fireEvent.change(configOption.closest('select') as HTMLSelectElement, { target: { value: 'missing-config' } });
    await waitFor(() => expect(stage.canaryConfig.canaryConfigId).toBe('config-1'));

    expect(stage.canaryConfig.canaryConfigId).toBe('config-1');
    expect(updateStage.mock.lastCall).toEqual([expect.objectContaining({ canaryConfig: stage.canaryConfig })]);
  });

  it('renders expression-valued lookback as static JSON editor guidance', async () => {
    const stage = defaultStage();
    (stage.canaryConfig as any).lookbackMins = '${ parameters.lookbackMins }';

    const { container } = await renderStage(stage);

    expect(container).toHaveTextContent(
      'Using a sliding lookback duration defined by an expression viewable in the pipeline JSON editor.',
    );
    expect(container.querySelector('[data-test-id="lookback-minutes-input"]')).not.toBeInTheDocument();
  });

  it('shows retrospective start and end fields only for retrospective analysis', async () => {
    const realTime = await renderStage(defaultStage());
    expect(realTime.container).not.toHaveTextContent('Start Time');
    expect(realTime.container).not.toHaveTextContent('End Time');

    const retrospectiveStage = defaultStage();
    retrospectiveStage.analysisType = KayentaAnalysisType.Retrospective;
    const retrospective = await renderStage(retrospectiveStage);
    expect(retrospective.container).toHaveTextContent('Start Time');
    expect(retrospective.container).toHaveTextContent('End Time');
  });

  it('shows real-time automatic baseline selectors only for real-time automatic analysis', async () => {
    const realTime = await renderStage(defaultStage());
    expect(realTime.container).not.toHaveTextContent('Baseline Version');

    const automaticStage = defaultStage();
    automaticStage.isNew = true;
    automaticStage.analysisType = KayentaAnalysisType.RealTimeAutomatic;
    const automatic = await renderStage(automaticStage);
    expect(automatic.container).toHaveTextContent('Baseline Version');
    expect(automatic.container).toHaveTextContent('Provider');
    expect(automatic.container).toHaveTextContent('Account');
    expect(automatic.container).toHaveTextContent('Cluster');
  });

  it('uses core provider services for server group pair add and edit actions', async () => {
    const getProviderConfig = vi.spyOn(CloudProviderRegistry, 'getValue').mockReturnValue({
      CloneServerGroupModal: cloneServerGroupModal,
    });
    const selectProvider = vi.spyOn(ProviderSelectionService, 'selectProvider').mockResolvedValue('aws');
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
    const { container } = await renderStage(stage);

    fireEvent.click(within(container).getAllByText('Edit')[0]);
    await waitFor(() => expect(getProviderConfig).toHaveBeenCalled());

    expect(getProviderConfig).toHaveBeenCalledWith('aws', 'serverGroup');
    const buildEditCommand = mockRuntimeServices.serverGroupCommandBuilder.buildServerGroupCommandFromPipeline as Mock;
    expect(buildEditCommand).toHaveBeenCalledTimes(1);
    expect(buildEditCommand.mock.calls[0][0]).toBe(application);
    expect(buildEditCommand.mock.calls[0][1]).toBe(control);
    expect(buildEditCommand.mock.calls[0].slice(2)).toEqual([null, null]);

    const emptyStage = defaultStage();
    emptyStage.analysisType = KayentaAnalysisType.RealTimeAutomatic;
    emptyStage.deployments = {
      baseline: { application: 'spinnaker' },
      serverGroupPairs: [],
    } as any;
    const empty = await renderStage(emptyStage);

    fireEvent.click(within(empty.container).getAllByRole('button', { name: /Add/ })[0]);
    await waitFor(() => expect(selectProvider).toHaveBeenCalled());

    expect(selectProvider).toHaveBeenCalledWith(application, 'serverGroup', expect.any(Function));
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
