import { mount } from 'enzyme';
import React from 'react';

import { AccountService, MapEditor } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { awsTagImageStage } from './awsTagImageStage';

describe('AwsTagImageStageConfig', () => {
  const application = {
    defaultCredentials: {},
    defaultRegions: {},
    getDataSource: () => ({ data: [] }),
  } as any;

  beforeEach(() => {
    spyOn(AccountService, 'listAccounts').and.returnValue(Promise.resolve([]) as any);
    spyOn(AccountService, 'getUniqueAttributeForAllAccounts').and.returnValue(Promise.resolve([]) as any);
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
    const stage = {
      cloudProvider: 'aws',
      consideredStages: ['bake-ref', 'stale-ref'],
      name: 'Tag image',
      refId: 'tag-image-ref',
      requisiteStageRefIds: ['bake-ref', 'wait-ref'],
      tags: { Owner: '', 'legacy:key': 'persisted' },
      type: 'upsertImageTags',
      ...stageOverrides,
    };
    const pipeline = { stages: [bake, findImage, wait, unrelatedBake, stage] } as any;
    const updateStageField = jasmine.createSpy('updateStageField');
    const StageComponent = awsTagImageStage.component as React.ComponentType<any>;

    const wrapper = mount(
      <StageComponent
        application={application}
        pipeline={pipeline}
        stage={stage}
        stageFieldUpdated={jasmine.createSpy('stageFieldUpdated')}
        updateStage={jasmine.createSpy('updateStage')}
        updateStageField={updateStageField}
      />,
    );

    return { stage, updateStageField, wrapper };
  }

  it('registers a dedicated editor instead of the generic Amazon stage editor', () => {
    expect(awsTagImageStage.component).not.toBe(AmazonStageConfig);
  });

  it('persists missing defaults once without causing an update loop', () => {
    const { stage, updateStageField, wrapper } = renderStage({ cloudProvider: undefined, tags: undefined });

    expect(updateStageField.calls.allArgs()).toEqual([[{ cloudProvider: 'aws', tags: {} }]]);

    wrapper.setProps({ stage: { ...stage, cloudProvider: 'aws', tags: {} } });

    expect(updateStageField).toHaveBeenCalledTimes(1);
  });

  it('preserves an explicit cloud provider while initializing missing tags', () => {
    const { updateStageField } = renderStage({ cloudProvider: 'aws-custom', tags: undefined });

    expect(updateStageField.calls.allArgs()).toEqual([[{ tags: {} }]]);
  });

  it('preserves explicit tags while initializing a missing cloud provider', () => {
    const tags = { Owner: '', unknown: 'persisted' };
    const { updateStageField, wrapper } = renderStage({ cloudProvider: undefined, tags });

    expect(updateStageField.calls.allArgs()).toEqual([[{ cloudProvider: 'aws' }]]);
    expect(wrapper.find(MapEditor).prop('model')).toBe(tags);
  });

  it('round-trips added, edited, and removed tags as structured objects', () => {
    const { stage, updateStageField, wrapper } = renderStage();
    const mapEditor = wrapper.find(MapEditor);

    expect(mapEditor.exists()).toBe(true);
    if (!mapEditor.exists()) {
      return;
    }

    expect(mapEditor.prop('allowEmpty')).toBe(true);
    expect(mapEditor.prop('model')).toEqual(stage.tags);

    const added = { ...stage.tags, Environment: 'production' };
    const edited = { ...added, 'legacy:key': 'updated' };
    const removed = { Owner: '', 'legacy:key': 'updated' };
    mapEditor.prop('onChange')(added, false);
    mapEditor.prop('onChange')(edited, false);
    mapEditor.prop('onChange')(removed, false);

    expect(updateStageField.calls.allArgs()).toEqual([[{ tags: added }], [{ tags: edited }], [{ tags: removed }]]);
  });

  it('offers only direct and indirect upstream image-producing stage refIds', () => {
    const { wrapper } = renderStage();
    const refIds = wrapper
      .find('input[type="checkbox"]')
      .map((input) => input.prop('value'))
      .sort();

    expect(refIds).toEqual(['bake-ref', 'find-image-ref', 'stale-ref']);
  });

  it('shows a selected stale stage ref as unavailable', () => {
    const { wrapper } = renderStage();
    const staleStage = wrapper.find('input[type="checkbox"][value="stale-ref"]');

    expect(staleStage.exists()).toBe(true);
    if (!staleStage.exists()) {
      return;
    }

    expect(staleStage.prop('checked')).toBe(true);
    expect(staleStage.prop('disabled')).toBe(true);
    expect(staleStage.closest('label').text()).toContain('stale-ref (unavailable)');
  });

  it('preserves a stale stage ref when selecting an available stage', () => {
    const { updateStageField, wrapper } = renderStage();
    const findImage = wrapper.find('input[type="checkbox"][value="find-image-ref"]');

    expect(findImage.exists()).toBe(true);
    if (!findImage.exists()) {
      return;
    }

    findImage.simulate('change', { target: { checked: true } });

    expect(updateStageField).toHaveBeenCalledWith({
      consideredStages: ['bake-ref', 'stale-ref', 'find-image-ref'],
    });
  });

  it('preserves a stale stage ref when deselecting an available stage', () => {
    const { updateStageField, wrapper } = renderStage();
    const bake = wrapper.find('input[type="checkbox"][value="bake-ref"]');

    expect(bake.exists()).toBe(true);
    if (!bake.exists()) {
      return;
    }

    bake.simulate('change', { target: { checked: false } });

    expect(updateStageField).toHaveBeenCalledWith({ consideredStages: ['stale-ref'] });
  });
});
