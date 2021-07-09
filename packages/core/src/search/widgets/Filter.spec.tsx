import React from 'react';
import { ReactWrapper, mount } from 'enzyme';

import { IFilterProps, Filter } from './Filter';
import { IFilterType } from './SearchFilterTypeRegistry';

describe('<Filter/>', () => {
  let component: ReactWrapper<IFilterProps, any>;

  function getNewFilterType(): IFilterType {
    return {
      key: 'region',
      name: 'Region',
    };
  }

  function getNewTagComponent(filterType: IFilterType, isActive: boolean): ReactWrapper<IFilterProps, any> {
    return mount(<Filter filterType={filterType} isActive={isActive} />);
  }

  it('should display a filter', () => {
    const filterType: IFilterType = getNewFilterType();
    component = getNewTagComponent(filterType, true);

    expect(component.render().hasClass('filter')).toBeTruthy();
    expect(component.find('div.filter__text').text()).toBe(filterType.name);
    expect(component.find('div.filter__modifier').text()).toBe(`[${filterType.key.toLocaleUpperCase()}:]`);
  });

  it('should set the tab focus class when active', () => {
    component = getNewTagComponent(getNewFilterType(), true);
    expect(component.render().hasClass('filter')).toBeTruthy();
    expect(component.render().hasClass('filter--focus')).toBeTruthy();
    expect(component.render().hasClass('filter--blur')).toBeFalsy();
  });

  it('should set the tab blur class when not active', () => {
    component = getNewTagComponent(getNewFilterType(), false);
    expect(component.render().hasClass('filter')).toBeTruthy();
    expect(component.render().hasClass('filter--focus')).toBeFalsy();
    expect(component.render().hasClass('filter--blur')).toBeTruthy();
  });
});
