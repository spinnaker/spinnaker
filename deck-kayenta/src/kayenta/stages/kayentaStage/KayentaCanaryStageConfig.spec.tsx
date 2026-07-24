import type { ReactWrapper } from 'enzyme';
import { mount } from 'enzyme';
import type { ICanaryConfig, IKayentaAccount, IKayentaStage } from 'kayenta/domain';
import { KayentaAccountType, KayentaAnalysisType } from 'kayenta/domain';
import { getCanaryConfigById, listKayentaAccounts } from 'kayenta/service/canaryConfig.service';
import React from 'react';
import { act } from 'react-dom/test-utils';

import type { DeckRuntimeServices } from '@spinnaker/core';
import { AccountService, CloudProviderRegistry, ProviderSelectionService } from '@spinnaker/core';

import { KayentaCanaryStageConfig } from './KayentaCanaryStageConfig';
import { addPair, editServerGroup } from './kayentaStageConfig.model';

const mockRuntimeServices = { serverGroupCommandBuilder: {}, serverGroupTransformer: {} } as DeckRuntimeServices;

jest.mock('kayenta/service/canaryConfig.service', () => ({
  getCanaryConfigById: jest.fn(),
  listKayentaAccounts: jest.fn(),
}));

jest.mock('kayenta/canary.settings', () => ({
  CanarySettings: {
    legacySiteLocalFieldsEnabled: false,
    metricsAccountName: 'metrics-account',
    storageAccountName: 'storage-account',
  },
}));

jest.mock('@spinnaker/core', () => ({
  AccountService: {
    listProviders: jest.fn(),
    listAccounts: jest.fn(),
  },
  AppListExtractor: {
    getClusters: jest.fn(() => ['baseline-cluster']),
  },
  AccountTag: ({ account }: { account: string }) => <span>{account}</span>,
  HelpField: ({ id }: { id: string }) => <span data-help-field={id} />,
  MapEditor: () => null,
  NameUtils: {
    getClusterName: jest.fn((application: string, stack: string, freeFormDetails: string) =>
      [application, stack, freeFormDetails].filter(Boolean).join('-'),
    ),
  },
  CloudProviderRegistry: { getValue: jest.fn() },
  ProviderSelectionService: { selectProvider: jest.fn() },
  StageConfigField: ({ label, children }: { label?: string; children: React.ReactNode }) => (
    <div className="form-group">
      {label && <label>{label}</label>}
      {children}
    </div>
  ),
  useDeckRuntimeServices: () => mockRuntimeServices,
}));

jest.mock('./kayentaStageConfig.model', () => {
  const actual = jest.requireActual('./kayentaStageConfig.model');
  return {
    ...actual,
    addPair: jest.fn(() => Promise.resolve()),
    editServerGroup: jest.fn(() => Promise.resolve()),
  };
});

describe('<KayentaCanaryStageConfig />', () => {
  const canaryConfig: ICanaryConfig = {
    id: 'config-1',
    name: 'Config One',
    applications: ['spinnaker'],
    metrics: [],
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
    ready: jest.fn(() => Promise.resolve()),
    getDataSource: jest.fn(() => ({ data: [{ id: 'config-1', name: 'Config One' }] })),
    serverGroups: { loaded: true, data: [] as any[] },
  };

  beforeEach(() => {
    jest.clearAllMocks();
    (getCanaryConfigById as jest.Mock).mockResolvedValue(canaryConfig);
    (listKayentaAccounts as jest.Mock).mockResolvedValue(kayentaAccounts);
    (AccountService.listProviders as jest.Mock).mockResolvedValue(['aws', 'gce']);
    (AccountService.listAccounts as jest.Mock).mockResolvedValue([{ name: 'prod', environment: 'prod' }]);
  });

  async function render(stage: IKayentaStage): Promise<ReactWrapper> {
    let wrapper: ReactWrapper;
    await act(async () => {
      wrapper = mount(
        React.createElement(KayentaCanaryStageConfig as React.ComponentType<any>, {
          application,
          stage,
          updateStage: jest.fn(),
        }) as any,
      );
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    wrapper.update();
    return wrapper;
  }

  it('passes runtime services to server group pair add and edit actions', async () => {
    const stage = {
      isNew: false,
      analysisType: KayentaAnalysisType.RealTimeAutomatic,
      canaryConfig: {
        canaryConfigId: 'config-1',
        canaryAnalysisIntervalMins: '5',
        lifetimeDuration: 'PT1H',
        beginCanaryAnalysisAfterMins: '0',
        scoreThresholds: { marginal: '75', pass: '95' },
        scopes: [{ scopeName: 'default', extendedScopeParams: {} }],
      },
      deployments: {
        baseline: { cloudProvider: 'aws', application: 'spinnaker' },
        serverGroupPairs: [
          {
            control: { cloudProvider: 'aws', account: 'prod', location: 'us-east-1', application: 'api' },
            experiment: { cloudProvider: 'aws', account: 'prod', location: 'us-east-1', application: 'api' },
          },
        ],
      },
    } as any;
    const wrapper = await render(stage);

    wrapper
      .find('a')
      .filterWhere((link) => link.text() === 'Edit')
      .first()
      .simulate('click');
    await act(async () => Promise.resolve());

    const editDependencies = (editServerGroup as jest.Mock).mock.calls[0][1];
    expect(editDependencies).toEqual(
      expect.objectContaining({
        application,
        cloudProviderRegistry: CloudProviderRegistry,
        providerSelectionService: ProviderSelectionService,
        runtimeServices: mockRuntimeServices,
      }),
    );
    expect(editDependencies).not.toHaveProperty('$uibModal');

    stage.deployments.serverGroupPairs = [];
    wrapper.setProps({ stage });
    wrapper.find('button.add-new').simulate('click');
    await act(async () => Promise.resolve());

    const addDependencies = (addPair as jest.Mock).mock.calls[0][1];
    expect(addDependencies).toEqual(
      expect.objectContaining({
        application,
        cloudProviderRegistry: CloudProviderRegistry,
        providerSelectionService: ProviderSelectionService,
        runtimeServices: mockRuntimeServices,
      }),
    );
    expect(addDependencies).not.toHaveProperty('$uibModal');

    wrapper.unmount();
  });
});
