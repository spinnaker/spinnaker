import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import { AccountService, StageConstants } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { getFormGroupByLabel } from '../../../../../core/src/utils/testUtils/rtl';
import { setupUser } from '../../../../../core/src/utils/testUtils/userEvent';
import { awsModifyWarmPoolStage } from './modifyWarmPoolStage';

describe('AWS Modify Warm Pool stage', () => {
  function renderEditor(stage: any = {}, pipeline: any = {}) {
    const updateStageField = vi.fn();
    const updateStage = vi.fn();
    const StageConfig = awsModifyWarmPoolStage.component;
    const stageModel = { type: 'modifyWarmPool', cloudProviderType: 'aws', ...stage };
    const rendered = render(
      <StageConfig
        {...({
          application: { defaultCredentials: {}, defaultRegions: {}, getDataSource: () => ({ data: [] }) },
          pipeline,
          stage: stageModel,
          updateStage,
          updateStageField,
        } as any)}
      />,
    );

    return { ...rendered, stage: stageModel, updateStage, updateStageField };
  }

  const combobox = (label: string) => within(getFormGroupByLabel(label)).getByRole('combobox');
  const spinbutton = (label: string) => within(getFormGroupByLabel(label)).getByRole('spinbutton');

  beforeEach(() => {
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([]);
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue([] as any);
  });

  it('registers a dedicated stage editor', () => {
    expect(awsModifyWarmPoolStage.component).not.toBe(AmazonStageConfig);
  });

  it('renders the AWS server group selectors for a pipeline stage', async () => {
    renderEditor({ target: 'current_asg' });

    await waitFor(() => expect(AccountService.getUniqueAttributeForAllAccounts).toHaveBeenCalled());
    expect(getFormGroupByLabel('Account')).toBeInTheDocument();
    expect(getFormGroupByLabel('Cluster')).toBeInTheDocument();

    const target = combobox('Target');
    expect(
      within(target)
        .getAllByRole('option')
        .map((option) => option.getAttribute('value')),
    ).toEqual(StageConstants.TARGET_LIST.map((option) => option.val));
    expect(target).toHaveValue('current_asg');
  });

  it('defaults the action to upsert and shows upsert-only fields', async () => {
    renderEditor();

    await waitFor(() => expect(AccountService.getUniqueAttributeForAllAccounts).toHaveBeenCalled());
    expect(combobox('Action')).toHaveValue('upsert');
    expect(spinbutton('Min Size')).toBeInTheDocument();
    expect(spinbutton('Max Group Prepared Capacity')).toBeInTheDocument();
    expect(combobox('Instance State')).toBeInTheDocument();
  });

  it('hides upsert-only fields when action is delete', async () => {
    renderEditor({ action: 'delete' });

    await waitFor(() => expect(AccountService.getUniqueAttributeForAllAccounts).toHaveBeenCalled());
    expect(screen.queryByText('Min Size')).not.toBeInTheDocument();
    expect(screen.queryByText('Max Group Prepared Capacity')).not.toBeInTheDocument();
    expect(screen.queryByText('Instance State')).not.toBeInTheDocument();
  });

  it('updates the action field on change', async () => {
    const user = setupUser();
    const { updateStageField } = renderEditor({ action: 'upsert' });

    await user.selectOptions(combobox('Action'), 'delete');

    expect(updateStageField).toHaveBeenCalledWith({ action: 'delete' });
  });

  it('updates warm pool fields on change', async () => {
    const user = setupUser();
    const { updateStageField } = renderEditor({ action: 'upsert' });

    fireEvent.change(spinbutton('Min Size'), { target: { value: '3' } });
    expect(updateStageField).toHaveBeenCalledWith({ minSize: 3 });

    await user.selectOptions(combobox('Instance State'), 'Running');
    expect(updateStageField).toHaveBeenCalledWith({ poolState: 'Running' });
  });
});
