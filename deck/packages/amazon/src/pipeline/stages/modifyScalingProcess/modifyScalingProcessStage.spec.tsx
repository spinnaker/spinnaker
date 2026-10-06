import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import { AccountService, StageConstants } from '@spinnaker/core';
import { getFormGroupByLabel } from '../../../../../core/src/utils/testUtils/rtl';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { awsModifyScalingProcessStage } from './modifyScalingProcessStage';

describe('AWS Modify Scaling Process stage', () => {
  beforeEach(() => {
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([]);
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue([]);
  });

  function renderEditor(stageOverrides: any = {}, pipeline: any = {}, applicationOverrides: any = {}) {
    const initialStage = { type: 'modifyAwsScalingProcess', cloudProviderType: 'aws', ...stageOverrides };
    const updateStage = vi.fn();
    const updateStageField = vi.fn();
    const StageConfig = awsModifyScalingProcessStage.component as React.ComponentType<any>;

    function StageHarness() {
      const [stage, setStage] = React.useState(initialStage);
      const update = (changes: any) => {
        updateStage(changes);
        setStage((current: any) => ({ ...current, ...changes }));
      };
      const updateField = (changes: any) => {
        updateStageField(changes);
        setStage((current: any) => ({ ...current, ...changes }));
      };
      return (
        <StageConfig
          application={{
            defaultCredentials: {},
            defaultRegions: {},
            getDataSource: () => ({ data: [] }),
            ...applicationOverrides,
          }}
          pipeline={pipeline}
          stage={stage}
          updateStage={update}
          updateStageField={updateField}
        />
      );
    }

    return { initialStage, updateStage, updateStageField, ...render(<StageHarness />) };
  }

  it('registers a dedicated stage editor', () => {
    expect(awsModifyScalingProcessStage.component).not.toBe(AmazonStageConfig);
  });

  it('renders AWS target, action, and scaling process controls', () => {
    renderEditor({ target: 'current_asg', action: 'suspend', processes: ['Launch'] }, { strategy: true });

    const target = within(getFormGroupByLabel('Target')).getByRole('combobox');
    expect(
      within(target)
        .getAllByRole('option')
        .map((option) => (option as HTMLOptionElement).value),
    ).toEqual(StageConstants.TARGET_LIST.map((option) => option.val));
    expect(target).toHaveValue('current_asg');
    expect(within(getFormGroupByLabel('Action')).getByRole('combobox')).toHaveValue('suspend');
    expect(screen.getByRole('checkbox', { name: 'Launch' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Terminate' })).not.toBeChecked();
  });

  it('reports process selections through the controlled stage contract', () => {
    const rendered = renderEditor({ action: 'suspend', processes: ['Launch'] }, { strategy: true });
    rendered.updateStageField.mockClear();

    fireEvent.click(screen.getByRole('checkbox', { name: 'Terminate' }));

    expect(rendered.updateStageField).toHaveBeenCalledWith({ processes: ['Launch', 'Terminate'] });
    expect(screen.getByRole('checkbox', { name: 'Terminate' })).toBeChecked();
  });

  it('reports normal selector changes through updateStage without mutating the stage prop', async () => {
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([{ name: 'test' }, { name: 'prod' }] as any);
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue(['eu-west-1', 'us-east-1'] as any);
    const application = {
      getDataSource: () => ({
        data: [
          { account: 'test', region: 'eu-west-1', cluster: 'app-main', moniker: { app: 'app', cluster: 'app-main' } },
          { account: 'prod', region: 'us-east-1', cluster: 'app-prod', moniker: { app: 'app', cluster: 'app-prod' } },
        ],
      }),
    };
    const accountRendered = renderEditor(
      {
        action: 'suspend',
        cloudProvider: 'aws',
        credentials: 'test',
        processes: [],
        regions: ['eu-west-1'],
        cluster: 'app-main',
        target: 'current_asg_dynamic',
      },
      { strategy: false },
      application,
    );
    const originalAccountStage = {
      ...accountRendered.initialStage,
      regions: [...accountRendered.initialStage.regions],
    };
    accountRendered.updateStage.mockClear();

    const account = within(getFormGroupByLabel('Account')).getByRole('combobox');
    await screen.findByRole('option', { name: 'prod' });
    fireEvent.change(account, { target: { value: 'prod' } });

    expect(accountRendered.initialStage).toEqual(originalAccountStage);
    expect(accountRendered.updateStage).toHaveBeenCalledExactlyOnceWith({
      type: 'modifyAwsScalingProcess',
      cloudProviderType: 'aws',
      cloudProvider: 'aws',
      credentials: 'prod',
      regions: ['eu-west-1'],
      cluster: undefined,
      processes: [],
      action: 'suspend',
      target: 'current_asg_dynamic',
    });
    accountRendered.unmount();

    const regionRendered = renderEditor(
      {
        action: 'suspend',
        cloudProvider: 'aws',
        credentials: 'test',
        processes: [],
        regions: ['eu-west-1'],
        cluster: 'app-main',
        target: 'current_asg_dynamic',
      },
      { strategy: false },
      application,
    );
    const originalRegionStage = { ...regionRendered.initialStage, regions: [...regionRendered.initialStage.regions] };
    regionRendered.updateStage.mockClear();
    fireEvent.click(await screen.findByRole('checkbox', { name: 'eu-west-1' }));

    expect(regionRendered.initialStage).toEqual(originalRegionStage);
    expect(regionRendered.updateStage).toHaveBeenCalledExactlyOnceWith({
      type: 'modifyAwsScalingProcess',
      cloudProviderType: 'aws',
      cloudProvider: 'aws',
      credentials: 'test',
      regions: [],
      cluster: undefined,
      processes: [],
      action: 'suspend',
      target: 'current_asg_dynamic',
    });
    regionRendered.unmount();

    const clusterRendered = renderEditor(
      {
        action: 'suspend',
        cloudProvider: 'aws',
        credentials: 'test',
        processes: [],
        regions: ['eu-west-1'],
        cluster: 'app-main',
        target: 'current_asg_dynamic',
      },
      { strategy: false },
      application,
    );
    const originalClusterStage = {
      ...clusterRendered.initialStage,
      regions: [...clusterRendered.initialStage.regions],
    };
    clusterRendered.updateStage.mockClear();
    fireEvent.change(within(getFormGroupByLabel('Cluster')).getByRole('combobox'), { target: { value: '' } });

    expect(clusterRendered.initialStage).toEqual(originalClusterStage);
    expect(clusterRendered.updateStage).toHaveBeenCalledExactlyOnceWith({
      type: 'modifyAwsScalingProcess',
      cloudProviderType: 'aws',
      cloudProvider: 'aws',
      credentials: 'test',
      regions: ['eu-west-1'],
      cluster: undefined,
      moniker: undefined,
      processes: [],
      action: 'suspend',
      target: 'current_asg_dynamic',
    });
  });

  it('supports suspend and resume while clearing only legacy action fields', () => {
    const processes = ['Launch', 'HealthCheck'];
    const rendered = renderEditor(
      { action: 'suspend', processes, suspendProcesses: ['Terminate'], resumeProcesses: ['AZRebalance'] },
      { strategy: true },
    );
    rendered.updateStageField.mockClear();

    fireEvent.change(within(getFormGroupByLabel('Action')).getByRole('combobox'), { target: { value: 'resume' } });

    const changes = rendered.updateStageField.mock.lastCall[0];
    expect(changes).toEqual({ action: 'resume', suspendProcesses: undefined, resumeProcesses: undefined });
    expect(Object.prototype.hasOwnProperty.call(changes, 'processes')).toBe(false);
    expect(rendered.initialStage.processes).toBe(processes);
  });

  it('normalizes legacy process fields when reopening without changing action', async () => {
    const processes = ['Launch', 'ScheduledActions'];
    const rendered = renderEditor(
      {
        cloudProvider: 'aws',
        credentials: 'test',
        regions: ['eu-west-1'],
        cluster: 'app-main',
        target: 'current_asg',
        action: 'suspend',
        processes,
        suspendProcesses: ['Terminate'],
        resumeProcesses: ['AZRebalance'],
      },
      { strategy: true },
    );

    await waitFor(() =>
      expect(rendered.updateStageField).toHaveBeenCalledWith({
        suspendProcesses: undefined,
        resumeProcesses: undefined,
      }),
    );
    expect(rendered.initialStage.processes).toBe(processes);
  });
});
