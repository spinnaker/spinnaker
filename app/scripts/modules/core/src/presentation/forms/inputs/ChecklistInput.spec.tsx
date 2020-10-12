import React from 'react';
import { mount } from 'enzyme';

import { ChecklistInput } from './ChecklistInput';

const noop = () => {};

describe('<ChecklistInput />', () => {
  it('initializes properly with provided values', () => {
    const checkedOptions = ['a', 'b', 'c'];
    const options = ['a', 'b', 'c', 'd'];
    const component = mount(<ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} />);
    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
  });

  it('updates items when an item is added externally', () => {
    const checkedOptions = ['a', 'b', 'c'];
    const options = ['a', 'b', 'c', 'd'];
    const component = mount(<ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} />);

    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
    component.setProps({ stringOptions: options.concat('e') });
    expect(component.find('input[type="checkbox"]').length).toBe(5);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
  });

  it('updates items when an item is removed externally', () => {
    const checkedOptions = ['a', 'b', 'c'];
    const options = ['a', 'b', 'c', 'd'];
    const component = mount(<ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} />);

    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
    component.setProps({ stringOptions: options.filter((item) => item !== 'c') });
    expect(component.find('input[type="checkbox"]').length).toBe(3);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(2);
  });

  it('updates checked items when an item is checked externally', () => {
    const checkedOptions = ['a', 'b', 'c'];
    const options = ['a', 'b', 'c', 'd'];
    const component = mount(<ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} />);

    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
    component.setProps({ value: checkedOptions.concat('d') });
    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(4);
  });

  it('updates checked items when an item is unchecked externally', () => {
    const checkedOptions = ['a', 'b', 'c'];
    const options = ['a', 'b', 'c', 'd'];
    const component = mount(<ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} />);

    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
    component.setProps({ value: checkedOptions.filter((item) => item !== 'c') });
    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(2);
  });

  it('shows the select all button when necessary', () => {
    const checkedOptions = ['a', 'b', 'c'];
    const options = ['a', 'b', 'c', 'd'];
    const component = mount(
      <ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} showSelectAll={true} />,
    );
    expect(component.find('a').length).toBe(1);
  });

  it('does not show the select all button when necessary', () => {
    const checkedOptions = ['a', 'b', 'c'];
    const options = ['a', 'b', 'c', 'd'];
    const component = mount(
      <ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} showSelectAll={false} />,
    );
    expect(component.find('a').length).toBe(0);
  });

  it('shows correct text for the select all button when not all the items are checked', () => {
    const checkedOptions = ['a', 'b', 'c'];
    const options = ['a', 'b', 'c', 'd'];
    const component = mount(
      <ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} showSelectAll={true} />,
    );

    expect(component.find('a').text()).toBe('Select All');
  });

  it('shows correct text for the select all button when all the items are checked', () => {
    const checkedOptions = ['a', 'b', 'c', 'd'];
    const options = ['a', 'b', 'c', 'd'];
    const component = mount(
      <ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} showSelectAll={true} />,
    );

    expect(component.find('a').text()).toBe('Deselect All');
  });

  it('passes an empty list to the onChange handler when deselect all clicked', () => {
    const checkedOptions = ['a', 'b', 'c', 'd'];
    const options = ['a', 'b', 'c', 'd'];
    const onChange = (e: React.ChangeEvent<any>): void => {
      expect(e.target.value.length).toBe(0);
    };
    const component = mount(
      <ChecklistInput value={checkedOptions} stringOptions={options} onChange={onChange} showSelectAll={true} />,
    );
    component.find('a').simulate('click');
  });

  it('passes a complete list to the onChange handler when select all clicked', () => {
    const checkedOptions = ['a'];
    const options = ['a', 'b', 'c', 'd'];
    const onChange = (e: React.ChangeEvent<any>): void => {
      expect(e.target.value.length).toBe(4);
    };
    const component = mount(
      <ChecklistInput value={checkedOptions} stringOptions={options} onChange={onChange} showSelectAll={true} />,
    );
    component.find('a').simulate('click');
  });
});
