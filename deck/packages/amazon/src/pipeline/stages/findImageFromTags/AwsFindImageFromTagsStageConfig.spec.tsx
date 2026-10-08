import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import { BakeryReader } from '@spinnaker/core';
import { getFormGroupByLabel } from '../../../../../core/src/utils/testUtils/rtl';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { AwsFindImageFromTagsStageConfig } from './AwsFindImageFromTagsStageConfig';
import { awsFindImageFromTagsStage } from './awsFindImageFromTagsStage';

describe('AWS Find Image from Tags stage', () => {
  function renderEditor(initialStage: any) {
    const updateStageField = vi.fn();
    function StageHarness() {
      const [stage, setStage] = React.useState(initialStage);
      const update = (changes: any) => {
        updateStageField(changes);
        setStage((current: any) => ({ ...current, ...changes }));
      };
      return (
        <AwsFindImageFromTagsStageConfig
          application={{ defaultRegions: { aws: 'eu-west-1' } } as any}
          pipeline={{} as any}
          stage={stage}
          updateStageField={update}
        />
      );
    }
    return { updateStageField, ...render(<StageHarness />) };
  }

  it('registers a dedicated stage editor', () => {
    expect(awsFindImageFromTagsStage.component).not.toBe(AmazonStageConfig);
  });

  it('renders explicit persisted values without changing them on mount', async () => {
    vi.spyOn(BakeryReader, 'getRegions').mockResolvedValue([] as any);
    const rendered = renderEditor({ cloudProvider: '', packageName: 'payments', regions: [], tags: { Owner: '' } });

    expect(within(getFormGroupByLabel('Package')).getByRole('textbox')).toHaveValue('payments');
    expect(within(getFormGroupByLabel('Tags')).getByLabelText('Key')).toHaveValue('Owner');
    await waitFor(() => expect(BakeryReader.getRegions).toHaveBeenCalledWith('aws'));
    expect(rendered.updateStageField).not.toHaveBeenCalled();
  });

  it('defaults only undefined stage fields on mount', async () => {
    vi.spyOn(BakeryReader, 'getRegions').mockResolvedValue([] as any);
    const rendered = renderEditor({ cloudProvider: undefined, regions: undefined, tags: undefined });

    await waitFor(() =>
      expect(rendered.updateStageField).toHaveBeenCalledExactlyOnceWith({
        cloudProvider: 'aws',
        regions: ['eu-west-1'],
        tags: {},
      }),
    );
  });

  it('loads AWS regions while retaining persisted selections as options', async () => {
    vi.spyOn(BakeryReader, 'getRegions').mockResolvedValue(['eu-west-1', 'us-east-1'] as any);
    const rendered = renderEditor({
      cloudProvider: 'aws',
      packageName: 'payments',
      regions: ['persisted-region'],
      tags: {},
    });

    expect(await screen.findByRole('checkbox', { name: 'persisted-region' })).toBeChecked();
    expect(await screen.findByRole('checkbox', { name: 'eu-west-1' })).not.toBeChecked();
    expect(await screen.findByRole('checkbox', { name: 'us-east-1' })).not.toBeChecked();
    expect(rendered.updateStageField).not.toHaveBeenCalled();
  });

  it('updates package, regions, and map-valued tags through the controlled stage contract', async () => {
    vi.spyOn(BakeryReader, 'getRegions').mockResolvedValue(['eu-west-1'] as any);
    const rendered = renderEditor({ packageName: 'payments', regions: [], tags: { Environment: 'production' } });
    rendered.updateStageField.mockClear();

    fireEvent.change(within(getFormGroupByLabel('Package')).getByRole('textbox'), { target: { value: 'transfers' } });
    fireEvent.click(await screen.findByRole('checkbox', { name: 'eu-west-1' }));
    fireEvent.change(within(getFormGroupByLabel('Tags')).getByLabelText('Value'), { target: { value: 'staging' } });
    fireEvent.click(within(getFormGroupByLabel('Tags')).getByRole('button', { name: 'Remove field' }));

    expect(rendered.updateStageField.mock.calls).toEqual([
      [{ packageName: 'transfers' }],
      [{ regions: ['eu-west-1'] }],
      [{ tags: { Environment: 'staging' } }],
      [{ tags: {} }],
    ]);
  });

  it('reports a newly added tag field through the controlled stage contract', () => {
    vi.spyOn(BakeryReader, 'getRegions').mockResolvedValue([] as any);
    const rendered = renderEditor({ packageName: 'payments', regions: [], tags: {} });
    rendered.updateStageField.mockClear();

    fireEvent.click(within(getFormGroupByLabel('Tags')).getByRole('button', { name: 'Add Field' }));

    expect(rendered.updateStageField).toHaveBeenCalledWith({ tags: { '': '' } });
    expect(within(getFormGroupByLabel('Tags')).getByLabelText('Key')).toHaveValue('');
  });
});
