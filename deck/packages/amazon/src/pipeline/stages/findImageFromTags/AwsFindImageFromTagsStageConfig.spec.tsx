import { mount, shallow } from 'enzyme';
import React from 'react';

import { BakeryReader, ChecklistInput, MapEditor } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { AwsFindImageFromTagsStageConfig } from './AwsFindImageFromTagsStageConfig';
import { awsFindImageFromTagsStage } from './awsFindImageFromTagsStage';

describe('AWS Find Image from Tags stage', () => {
  function renderEditor(stage: any) {
    const updateStageField = jasmine.createSpy('updateStageField');
    const wrapper = shallow(
      <AwsFindImageFromTagsStageConfig
        application={{ defaultRegions: { aws: 'eu-west-1' } } as any}
        pipeline={{} as any}
        stage={stage}
        updateStageField={updateStageField}
      />,
    );
    return { updateStageField, wrapper };
  }

  it('registers a dedicated stage editor', () => {
    expect(awsFindImageFromTagsStage.component).not.toBe(AmazonStageConfig);
  });

  it('renders explicit persisted values without changing them on mount', () => {
    spyOn(BakeryReader, 'getRegions').and.returnValue(Promise.resolve([]) as any);
    const tags = { Environment: 'production', EmptyValue: '' };
    const updateStageField = jasmine.createSpy('updateStageField');
    const wrapper = mount(
      <AwsFindImageFromTagsStageConfig
        application={{ defaultRegions: { aws: 'eu-west-1' } } as any}
        pipeline={{} as any}
        stage={{ cloudProvider: '', packageName: 'payments', regions: [], tags }}
        updateStageField={updateStageField}
      />,
    );

    expect(wrapper.find('input[name="packageName"]').prop('value')).toBe('payments');
    expect(wrapper.find(ChecklistInput).prop('value')).toEqual([]);
    expect(wrapper.find(MapEditor).prop('model')).toBe(tags);
    expect(wrapper.find(MapEditor).prop('allowEmpty')).toBe(true);
    expect(updateStageField).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('defaults only undefined stage fields on mount', () => {
    spyOn(BakeryReader, 'getRegions').and.returnValue(Promise.resolve([]) as any);
    const updateStageField = jasmine.createSpy('updateStageField');

    const wrapper = mount(
      <AwsFindImageFromTagsStageConfig
        application={{ defaultRegions: { aws: 'eu-west-1' } } as any}
        pipeline={{} as any}
        stage={{ cloudProvider: undefined, regions: undefined, tags: undefined }}
        updateStageField={updateStageField}
      />,
    );

    expect(updateStageField).toHaveBeenCalledOnceWith({ cloudProvider: 'aws', regions: ['eu-west-1'], tags: {} });
    wrapper.unmount();
  });

  it('loads AWS regions while retaining persisted selections as options', async () => {
    spyOn(BakeryReader, 'getRegions').and.returnValue(Promise.resolve(['eu-west-1', 'us-east-1']) as any);
    const updateStageField = jasmine.createSpy('updateStageField');
    const wrapper = mount(
      <AwsFindImageFromTagsStageConfig
        application={{} as any}
        pipeline={{} as any}
        stage={{ cloudProvider: 'aws', packageName: 'payments', regions: ['persisted-region'], tags: {} }}
        updateStageField={updateStageField}
      />,
    );

    await Promise.resolve();
    await Promise.resolve();
    wrapper.update();

    expect(BakeryReader.getRegions).toHaveBeenCalledWith('aws');
    expect(wrapper.find(ChecklistInput).prop('stringOptions')).toEqual(['persisted-region', 'eu-west-1', 'us-east-1']);
    expect(updateStageField).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('updates the package through the stage config callback', () => {
    const { updateStageField, wrapper } = renderEditor({ packageName: 'payments', regions: [], tags: {} });

    wrapper.find('input[name="packageName"]').simulate('change', { target: { value: 'transfers' } });

    expect(updateStageField).toHaveBeenCalledWith({ packageName: 'transfers' });
  });

  it('updates regions through the stage config callback', () => {
    const { updateStageField, wrapper } = renderEditor({ packageName: 'payments', regions: [], tags: {} });

    wrapper.find(ChecklistInput).prop('onChange')({ target: { value: ['eu-west-1', 'us-east-1'] } } as any);

    expect(updateStageField).toHaveBeenCalledWith({ regions: ['eu-west-1', 'us-east-1'] });
  });

  it('persists map-valued tag additions, edits, and removals through the stage config callback', () => {
    const { updateStageField, wrapper } = renderEditor({
      packageName: 'payments',
      regions: [],
      tags: { Environment: 'production' },
    });
    const onChange = wrapper.find(MapEditor).prop('onChange');

    onChange({ Environment: 'production', Team: '' }, false);
    onChange({ Environment: 'staging' }, false);
    onChange({}, false);

    expect(updateStageField.calls.allArgs()).toEqual([
      [{ tags: { Environment: 'production', Team: '' } }],
      [{ tags: { Environment: 'staging' } }],
      [{ tags: {} }],
    ]);
  });
});
