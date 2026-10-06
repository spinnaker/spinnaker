import { act, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';
import type { Mock } from 'vitest';

import { AccountService } from '@spinnaker/core';

import { AmazonStageConfig, getAmazonStageFields } from './AmazonStageConfig';
import { getFormGroupByLabel } from '../../../../core/src/utils/testUtils/rtl';

describe('AmazonStageConfig', () => {
  beforeEach(() => {
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([{ name: 'test', type: 'aws' }] as any);
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue(['us-east-1'] as any);
    vi.spyOn(AccountService, 'getAllAccountDetailsForProvider').mockResolvedValue([] as any);
  });

  function renderStage(stageOverrides: any = {}) {
    const application = { defaultCredentials: {}, defaultRegions: {}, getDataSource: () => ({ data: [] }) };
    return render(
      <AmazonStageConfig
        application={application as any}
        pipeline={{} as any}
        stage={{ type: 'destroyServerGroup', cloudProviderType: 'aws', ...stageOverrides }}
        updateStageField={vi.fn() as any}
      />,
    );
  }

  it('defines generic fields only for simple scalar stages', () => {
    const simpleStageTypes = ['destroyAsg', 'destroyServerGroup', 'findAmi', 'findImage'];
    const dedicatedStageTypes = [
      'bake',
      'cloneServerGroup',
      'deployCloudFormation',
      'disableAsg',
      'disableCluster',
      'disableServerGroup',
      'enableAsg',
      'enableServerGroup',
      'findImageFromTags',
      'modifyAwsScalingProcess',
      'modifyScalingProcess',
      'resizeAsg',
      'resizeServerGroup',
      'rollbackCluster',
      'scaleDownCluster',
      'shrinkCluster',
      'upsertImageTags',
    ];
    const fallbackFields = getAmazonStageFields({ type: 'unknown' });

    expect(
      [...simpleStageTypes, ...dedicatedStageTypes].filter((type) => getAmazonStageFields({ type }) !== fallbackFields),
    ).toEqual(simpleStageTypes);
  });

  it('renders account as a selector for target server group stages', async () => {
    renderStage({ credentials: 'test' });

    await waitFor(() => expect(within(getFormGroupByLabel('Account')).getByRole('combobox')).toHaveValue('test'));
    expect(within(getFormGroupByLabel('Account')).queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('keeps server group target as a selector', async () => {
    renderStage({ target: 'current_asg' });

    await waitFor(() =>
      expect(within(getFormGroupByLabel('Server Group')).getByRole('combobox')).toHaveValue('current_asg'),
    );
    expect(screen.getByRole('option', { name: /current/i })).toHaveAttribute('value', 'current_asg');
  });

  it('does not update account and region state after unmount', async () => {
    let resolveAccounts: (accounts: any[]) => void;
    let resolveRegions: (regions: string[]) => void;
    (AccountService.listAccounts as Mock).mockReturnValue(new Promise((resolve) => (resolveAccounts = resolve)) as any);
    (AccountService.getUniqueAttributeForAllAccounts as Mock).mockReturnValue(
      new Promise((resolve) => (resolveRegions = resolve)) as any,
    );
    const consoleError = vi.spyOn(console, 'error').mockReturnValue(undefined);
    const rendered = renderStage();
    rendered.unmount();

    await act(async () => {
      resolveAccounts!([{ name: 'test', type: 'aws' }]);
      resolveRegions!(['us-east-1']);
      await Promise.resolve();
    });

    expect(consoleError.mock.calls.join('\n')).not.toContain(
      "Can't perform a React state update on an unmounted component",
    );
  });
});
