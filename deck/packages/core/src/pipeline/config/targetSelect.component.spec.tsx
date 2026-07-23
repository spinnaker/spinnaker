import { mount } from 'enzyme';
import React from 'react';

import { targetSelectComponent, TargetSelect } from './targetSelect.component';

describe('TargetSelect', () => {
  it('registers the Angular component through the React bridge', () => {
    expect(targetSelectComponent.templateUrl).toBeUndefined();
    expect(targetSelectComponent.controller).toBeDefined();
  });

  it('renders without the AngularJS adapter and updates the model target', () => {
    const onChange = jasmine.createSpy('onChange');
    const model = { target: 'current_asg_dynamic' };
    const component = mount(<TargetSelect model={model} onChange={onChange} options={targetOptions()} />);

    expect(component.find(`.Angular${'JS'}Adapter`).exists()).toBe(false);
    component.find('input.target-select-search').simulate('focus');
    component.find('button.target-select-option').at(1).simulate('click');

    expect(model.target).toBe('ancestor_asg_dynamic');
    expect(onChange).toHaveBeenCalledWith('ancestor_asg_dynamic');
  });

  it('renders descriptions and filters options by search text', () => {
    const component = mount(
      <TargetSelect model={{ target: '' }} onChange={jasmine.createSpy()} options={targetOptions()} />,
    );

    component.find('input.target-select-search').simulate('focus');

    expect(component.find('.target-select-description').map((node) => node.text())).toContain('Previous server group');

    component.find('input.target-select-search').simulate('change', { target: { value: 'current' } });

    expect(component.find('button.target-select-option').map((node) => node.text())).toEqual([
      'CurrentCurrent server group',
    ]);
  });

  it('supports clearing to None', () => {
    const onChange = jasmine.createSpy('onChange');
    const model = { target: 'current_asg_dynamic' };
    const component = mount(<TargetSelect model={model} onChange={onChange} options={targetOptions()} />);

    component.find('button.target-select-clear').simulate('click');

    expect(model.target).toBe('');
    expect(onChange).toHaveBeenCalledWith('');
    expect(component.find('input.target-select-search').prop('placeholder')).toBe('None');
  });
});

function targetOptions() {
  return [
    { val: 'current_asg_dynamic', label: 'Current', description: 'Current server group' },
    { val: 'ancestor_asg_dynamic', label: 'Previous', description: 'Previous server group' },
  ] as any;
}
