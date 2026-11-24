import { fireEvent, render, screen, within } from '@testing-library/react';
import React from 'react';

import { AccountService } from '@spinnaker/core';
import { getFormGroupByLabel } from '../../../../../core/src/utils/testUtils/rtl';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { awsTagImageStage } from './awsTagImageStage';

describe('AwsTagImageStageConfig', () => {
  beforeEach(() => {
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([] as any);
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue([] as any);
  });

  function renderStage(stageOverrides: Record<string, any> = {}) {
    const bake = { name: 'Bake image', refId: 'bake-ref', requisiteStageRefIds: [], type: 'bake' };
    const findImage = {
      name: 'Find image',
      refId: 'find-image-ref',
      requisiteStageRefIds: [],
      type: 'findImageFromTags',
    };
    const wait = { name: 'Wait', refId: 'wait-ref', requisiteStageRefIds: ['find-image-ref'], type: 'wait' };
    const unrelatedBake = {
      name: 'Unrelated bake',
      refId: 'unrelated-bake-ref',
      requisiteStageRefIds: [],
      type: 'bake',
    };
    const initialStage = {
      cloudProvider: 'aws',
      consideredStages: ['bake-ref', 'stale-ref'],
      name: 'Tag image',
      refId: 'tag-image-ref',
      requisiteStageRefIds: ['bake-ref', 'wait-ref'],
      tags: { Owner: '', 'legacy:key': 'persisted' },
      type: 'upsertImageTags',
      ...stageOverrides,
    };
    const pipeline = { stages: [bake, findImage, wait, unrelatedBake, initialStage] } as any;
    const updateStageField = vi.fn();
    const StageComponent = awsTagImageStage.component as React.ComponentType<any>;
    function StageHarness() {
      const [stage, setStage] = React.useState(initialStage);
      const update = (changes: any) => {
        updateStageField(changes);
        setStage((current: any) => ({ ...current, ...changes }));
      };
      return <StageComponent application={{} as any} pipeline={pipeline} stage={stage} updateStageField={update} />;
    }
    return { initialStage, updateStageField, ...render(<StageHarness />) };
  }

  it('registers a dedicated editor instead of the generic Amazon stage editor', () => {
    expect(awsTagImageStage.component).not.toBe(AmazonStageConfig);
  });

  it('persists only missing defaults without causing an update loop', () => {
    const rendered = renderStage({ cloudProvider: undefined, tags: undefined });
    expect(rendered.updateStageField.mock.calls).toEqual([[{ cloudProvider: 'aws', tags: {} }]]);
  });

  it('preserves explicit values while initializing the other missing field', () => {
    expect(renderStage({ cloudProvider: 'aws-custom', tags: undefined }).updateStageField.mock.calls).toEqual([
      [{ tags: {} }],
    ]);
    const tags = { Owner: '', unknown: 'persisted' };
    const rendered = renderStage({ cloudProvider: undefined, tags });
    expect(rendered.updateStageField.mock.calls).toEqual([[{ cloudProvider: 'aws' }]]);
    expect(
      within(getFormGroupByLabel('Tags', rendered.container))
        .getAllByLabelText('Key')
        .map((input) => input.getAttribute('value')),
    ).toEqual(['Owner', 'unknown']);
  });

  it('round-trips edited and removed tags as structured objects', () => {
    const rendered = renderStage();
    rendered.updateStageField.mockClear();
    const tags = getFormGroupByLabel('Tags');

    fireEvent.change(within(tags).getAllByLabelText('Value')[1], { target: { value: 'updated' } });
    fireEvent.click(within(tags).getAllByRole('button', { name: 'Remove field' })[0]);

    expect(rendered.updateStageField.mock.calls).toEqual([
      [{ tags: { Owner: '', 'legacy:key': 'updated' } }],
      [{ tags: { 'legacy:key': 'updated' } }],
    ]);
  });

  it('reports a newly added tag field through the controlled stage contract', () => {
    const rendered = renderStage({ tags: {} });
    rendered.updateStageField.mockClear();

    fireEvent.click(within(getFormGroupByLabel('Tags')).getByRole('button', { name: 'Add Field' }));

    expect(rendered.updateStageField).toHaveBeenCalledWith({ tags: { '': '' } });
    expect(within(getFormGroupByLabel('Tags')).getByLabelText('Key')).toHaveValue('');
  });

  it('offers only upstream image-producing stages and marks stale selections unavailable', () => {
    renderStage();
    expect(screen.getByRole('checkbox', { name: 'Bake image' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Find image' })).not.toBeChecked();
    expect(screen.queryByRole('checkbox', { name: 'Unrelated bake' })).not.toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'stale-ref (unavailable)' })).toBeDisabled();
  });

  it('preserves stale refs when selecting and deselecting available stages', () => {
    const selected = renderStage();
    selected.updateStageField.mockClear();
    fireEvent.click(screen.getByRole('checkbox', { name: 'Find image' }));
    expect(selected.updateStageField).toHaveBeenCalledWith({
      consideredStages: ['bake-ref', 'stale-ref', 'find-image-ref'],
    });

    selected.updateStageField.mockClear();
    fireEvent.click(screen.getByRole('checkbox', { name: 'Bake image' }));
    expect(selected.updateStageField).toHaveBeenCalledWith({ consideredStages: ['stale-ref', 'find-image-ref'] });
  });
});
