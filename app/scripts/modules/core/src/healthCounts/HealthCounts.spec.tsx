import React from 'react';
import { mount, shallow } from 'enzyme';

import { HealthCounts } from './HealthCounts';
import { IInstanceCounts } from '../domain';

describe('<HealthCounts />', () => {
  it('displays nothing when container has no health info', () => {
    const container = {} as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.children().length).toBe(0);
  });

  it('displays only up count when only up count is provided', () => {
    const container = { up: 2 } as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.find('span.counter').length).toBe(1);
    expect(component.find('.glyphicon-Up-triangle').length).toBe(1);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Unknown-triangle').length).toBe(0);
    expect(component.find('span.healthy').length).toBe(2);
    expect(component.find('span.healthy').at(1).text().trim()).toBe('100%');
  });

  it('displays only down count when only down count is provided', () => {
    const container = { down: 2 } as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.find('span.counter').length).toBe(1);
    expect(component.find('.glyphicon-Up-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(1);
    expect(component.find('.glyphicon-Unknown-triangle').length).toBe(0);
    expect(component.find('span.dead').length).toBe(2);
    expect(component.find('span.dead').at(1).text().trim()).toBe('0%');
  });

  it('displays only unknown count when only unknown count is provided', () => {
    const container = { unknown: 2 } as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.find('span.counter').length).toBe(1);
    expect(component.find('.glyphicon-Up-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Unknown-triangle').length).toBe(1);
    expect(component.find('span.unknown').length).toBe(1);
    expect(component.find('span.dead').length).toBe(0);
  });

  it('displays only unknown count when only starting count is provided', () => {
    const container = { starting: 2 } as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.find('span.counter').length).toBe(1);
    expect(component.find('.glyphicon-Up-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Unknown-triangle').length).toBe(1);
    expect(component.find('span.unknown').length).toBe(1);
    expect(component.find('span.dead').length).toBe(1);
  });

  it('displays only total unknown count when only starting and unknown counts are provided', () => {
    const container = { starting: 1, unknown: 1 } as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.find('span.counter').length).toBe(1);
    expect(component.find('.glyphicon-Up-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Unknown-triangle').length).toBe(1);
    expect(component.find('span.unknown').length).toBe(1);
    expect(component.find('span.dead').length).toBe(1);
  });

  it('displays only outOfService minus when only outOfService count is provided', () => {
    const container = { outOfService: 2 } as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.find('span.counter').length).toBe(1);
    expect(component.find('.glyphicon-Up-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(0);
    expect(component.find('.glyphicon-minus').length).toBe(1);
    expect(component.find('span.dead').length).toBe(0);
  });

  it('displays up and outOfService when up and outOfService counts are provided', () => {
    const container = { up: 2, outOfService: 2 } as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.find('span.counter').length).toBe(1);
    expect(component.find('.glyphicon-Up-triangle').length).toBe(1);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(0);
    expect(component.find('.glyphicon-OutOfService-triangle').length).toBe(1);
    expect(component.find('span.healthy').length).toBe(2);
    expect(component.find('span.healthy').at(1).text().trim()).toBe('100%');
  });

  it('displays only succeeded count when only succeeded count is provided', () => {
    const container = { succeeded: 2 } as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.find('span.counter').length).toBe(1);
    expect(component.find('.glyphicon-Up-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Succeeded-triangle').length).toBe(1);
    expect(component.find('span.healthy').length).toBe(1);
  });

  it('displays only failed count when only failed count is provided', () => {
    const container = { failed: 2 } as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.find('span.counter').length).toBe(1);
    expect(component.find('.glyphicon-Up-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Failed-triangle').length).toBe(1);
    expect(component.find('span.dead').length).toBe(1);
  });

  it('displays up and down counts when up and down counts are provided', () => {
    const container = { up: 2, down: 2 } as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.find('span.counter').length).toBe(1);
    expect(component.find('.glyphicon-Up-triangle').length).toBe(1);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(1);
    expect(component.find('.glyphicon-OutOfService-triangle').length).toBe(0);
    expect(component.find('span.dead').length).toBe(1);
    expect(component.find('span.unhealthy').length).toBe(1);
    expect(component.find('span.unhealthy').text().trim()).toBe('50%');
  });

  it('displays up and unknown counts when up and unknown counts are provided', () => {
    const container = { up: 2, unknown: 2 } as IInstanceCounts;
    const component = shallow(<HealthCounts container={container} />);
    expect(component.find('span.counter').length).toBe(1);
    expect(component.find('.glyphicon-Up-triangle').length).toBe(1);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(0);
    expect(component.find('.glyphicon-Unknown-triangle').length).toBe(1);
    expect(component.find('.glyphicon-OutOfService-triangle').length).toBe(0);
    expect(component.find('span.unknown').length).toBe(1);
    expect(component.find('span.unhealthy').length).toBe(1);
    expect(component.find('span.unhealthy').text().trim()).toBe('50%');
  });

  it('updates when counters change', () => {
    let container = { up: 2, down: 2 } as IInstanceCounts;
    const component = mount(<HealthCounts container={container} />);
    expect(component.find('span.unhealthy').text().trim()).toBe('50%');
    expect(component.find('.glyphicon-Up-triangle').length).toBe(1);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(1);
    container = { up: 3, down: 1 } as IInstanceCounts;
    component.setProps({ container });
    expect(component.find('span.unhealthy').text().trim()).toBe('75%');
    expect(component.find('.glyphicon-Up-triangle').length).toBe(1);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(1);
    container = { up: 4, down: 0 } as IInstanceCounts;
    component.setProps({ container });
    expect(component.find('span.healthy').at(1).text().trim()).toBe('100%');
    expect(component.find('.glyphicon-Up-triangle').length).toBe(1);
    expect(component.find('.glyphicon-Down-triangle').length).toBe(0);
  });
});
