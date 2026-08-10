import { shallow } from 'enzyme';
import React from 'react';

import { StageConfigField } from '@spinnaker/core';

import { GceFindImageStageConfig } from './googleStageConfig';

describe('GCE find image onlyEnabled control', () => {
  function renderStage(stage, updateStage = jasmine.createSpy('updateStage')) {
    const wrapper = shallow(React.createElement(GceFindImageStageConfig, { application: {}, stage, updateStage }), {
      disableLifecycleMethods: true,
    });
    const field = wrapper.find(StageConfigField).filterWhere((node) => node.prop('label') === 'Server Group Filters');

    return { checkbox: field.find('input[type="checkbox"]'), field, updateStage };
  }

  it('defaults to considering only enabled server groups', () => {
    const stage = {};
    const { checkbox, field } = renderStage(stage);

    expect(stage.onlyEnabled).toBe(true);
    expect(field.exists()).toBe(true);
    expect(field.find('label').text().trim()).toBe('Only consider enabled Server Groups');
    expect(checkbox.prop('checked')).toBe(true);
  });

  it('preserves an explicit false value', () => {
    const stage = { onlyEnabled: false };
    const { checkbox } = renderStage(stage);

    expect(stage.onlyEnabled).toBe(false);
    expect(checkbox.prop('checked')).toBe(false);
  });

  it('updates onlyEnabled with the checkbox value', () => {
    const stage = { onlyEnabled: true };
    const { checkbox, updateStage } = renderStage(stage);

    checkbox.simulate('change', { target: { checked: false } });

    expect(updateStage).toHaveBeenCalledWith(jasmine.objectContaining({ onlyEnabled: false }));
  });
});
