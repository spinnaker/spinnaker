import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { AccountService } from '@spinnaker/core';

import { GceFindImageStageConfig } from './googleStageConfig';

vi.mock('@spinnaker/core', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, AccountRegionClusterSelector: () => null };
});

describe('GCE find image onlyEnabled control', () => {
  beforeEach(() => vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([]));

  function renderStage(stage, updateStage = vi.fn()) {
    const rendered = render(React.createElement(GceFindImageStageConfig, { application: {}, stage, updateStage }));
    return {
      ...rendered,
      checkbox: screen.getByRole('checkbox', { name: 'Only consider enabled Server Groups' }),
      updateStage,
    };
  }

  it('defaults to considering only enabled server groups', () => {
    const stage = {};
    const { checkbox } = renderStage(stage);

    expect(stage.onlyEnabled).toBe(true);
    expect(screen.getByText('Server Group Filters')).toBeInTheDocument();
    expect(checkbox).toBeChecked();
  });

  it('preserves an explicit false value', () => {
    const stage = { onlyEnabled: false };
    const { checkbox } = renderStage(stage);

    expect(stage.onlyEnabled).toBe(false);
    expect(checkbox).not.toBeChecked();
  });

  it('updates onlyEnabled with the checkbox value', () => {
    const stage = { onlyEnabled: true };
    const { checkbox, updateStage } = renderStage(stage);

    fireEvent.click(checkbox);

    expect(updateStage).toHaveBeenCalledWith(expect.objectContaining({ onlyEnabled: false }));
  });
});
