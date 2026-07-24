import { shallow } from 'enzyme';
import React from 'react';

import { FilterCollapse } from './FilterCollapse';
import { Tooltip } from '../presentation';

describe('FilterCollapse', () => {
  it('renders and toggles solely from controlled expansion state', () => {
    const onToggle = jasmine.createSpy('onToggle');
    const wrapper = shallow(<FilterCollapse filtersExpanded={false} onToggle={onToggle} />);

    expect(wrapper.find('.filters-hidden').length).toBe(1);
    expect(wrapper.find(Tooltip).prop('value')).toBe('Show filters');
    expect(wrapper.find('button').type()).toBe('button');
    expect(wrapper.find('button').hasClass('pin')).toBe(true);
    expect(wrapper.find('.fa-forward').length).toBe(1);
    expect(wrapper.find('.show-filter-text').text()).toContain('Show filters');

    wrapper.find('button').simulate('click');
    expect(onToggle).toHaveBeenCalledTimes(1);

    wrapper.setProps({ filtersExpanded: true });
    expect(wrapper.find('.filters-open').length).toBe(1);
    expect(wrapper.find(Tooltip).prop('value')).toBe('Hide filters');
    expect(wrapper.find('button').type()).toBe('button');
    expect(wrapper.find('button').hasClass('unpin')).toBe(true);
    expect(wrapper.find('.fa-backward').length).toBe(1);
    expect(wrapper.text()).toContain('Filters');

    wrapper.find('button').simulate('click');
    expect(onToggle).toHaveBeenCalledTimes(2);
  });
});
