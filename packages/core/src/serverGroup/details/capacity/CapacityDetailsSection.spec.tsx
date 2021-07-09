import React from 'react';
import { mount } from 'enzyme';

import { CapacityDetailsSection } from './CapacityDetailsSection';

describe('<CapacityDetailsSection/>', function () {
  it('when min === max, it should be displayed in simplemode', function () {
    const component = mount(<CapacityDetailsSection capacity={{ min: 9, max: 9, desired: 4 }} current={5} />);
    expect(component.contains(<dt>Min/Max</dt>)).toBe(true);
  });

  it('when min !== max, it should display the different min and max separately', function () {
    const component = mount(<CapacityDetailsSection capacity={{ min: 1, max: 9, desired: 4 }} current={5} />);
    expect(component.contains(<dt>Min</dt>)).toBe(true);
    expect(component.contains(<dt>Max</dt>)).toBe(true);
  });
});
