import React from 'react';
import { mount } from 'enzyme';
import { SelectInput } from './SelectInput';

const noop = () => {};

describe('<SelectInput />', () => {
  it('renders a select with options', () => {
    const value = 'b';
    const options = ['a', 'b', 'c', 'd'];
    const wrapper = mount(<SelectInput value={value} options={options} onChange={noop} />);
    expect(wrapper.find('.SelectInput').length).toBe(1);
    expect(wrapper.find('select').length).toBe(1);
    expect(wrapper.find('option').length).toBe(4);
  });

  it('updates the selected item using the value prop', () => {
    const value = 'b';
    const options = ['a', 'b', 'c', 'd'];
    const wrapper = mount(<SelectInput value={value} options={options} onChange={noop} />);
    expect(wrapper.find('select').getDOMNode<HTMLSelectElement>().value).toBe('b');
    wrapper.setProps({ value: 'c' });
    expect(wrapper.find('select').getDOMNode<HTMLSelectElement>().value).toBe('c');
  });

  it('wires the onChange handler to the selected item', () => {
    const value = 'b';
    const options = ['a', 'b', 'c', 'd'];
    const spy = jasmine.createSpy('onChange');
    const component = mount(<SelectInput value={value} options={options} onChange={spy} />);
    component.find('select').simulate('change', { target: { value: 'c' } });
    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy.calls.mostRecent().args[0].target.value).toBe('c');
  });

  describe('defaultValue prop', () => {
    it('causes the onChange handler to be called with a default value when no value is set', () => {
      const value = undefined as string;
      const options = ['a', 'b', 'c', 'd'];
      const spy = jasmine.createSpy('onChange');
      mount(<SelectInput value={value} defaultValue={options[0]} options={options} onChange={spy} />);
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy.calls.mostRecent().args[0].target.value).toBe('a');
    });

    it('causes the onChange handler to be called with a default value when an invalid value is set', () => {
      const value = 'x';
      const options = ['a', 'b', 'c', 'd'];
      const spy = jasmine.createSpy('onChange');
      mount(<SelectInput value={value} defaultValue={options[0]} options={options} onChange={spy} />);
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy.calls.mostRecent().args[0].target.value).toBe('a');
    });

    it('does not call the onChange handler if no defaultValue is provided', () => {
      const value = 'x';
      const options = ['a', 'b', 'c', 'd'];
      const spy = jasmine.createSpy('onChange');
      mount(<SelectInput value={value} options={options} onChange={spy} />);
      expect(spy).not.toHaveBeenCalled();
    });
  });
});
