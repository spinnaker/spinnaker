import React from 'react';
import { mount } from 'enzyme';
import { RadioButtonInput } from './RadioButtonInput';

const noop = () => {};

describe('<RadioButtonInput />', () => {
  it('renders radio button inputs', () => {
    const value = 'b';
    const options = ['a', 'b', 'c', 'd'];
    const wrapper = mount(<RadioButtonInput value={value} stringOptions={options} onChange={noop} />);
    expect(wrapper.find('.RadioButtonInput').length).toBe(1);
    expect(wrapper.find('input[type="radio"]').length).toBe(4);
  });

  it('updates the selected item using the value prop', () => {
    const value = 'b';
    const options = ['a', 'b', 'c', 'd'];
    const wrapper = mount(<RadioButtonInput value={value} stringOptions={options} onChange={noop} />);
    expect(wrapper.find('input[type="radio"][checked=true]').getDOMNode<any>().value).toBe('b');
    wrapper.setProps({ value: 'c' });
    expect(wrapper.find('input[type="radio"][checked=true]').getDOMNode<any>().value).toBe('c');
  });

  it('wires the onChange handler to the radios', () => {
    const value = 'b';
    const options = ['a', 'b', 'c', 'd'];
    const spy = jasmine.createSpy('onChange');
    const wrapper = mount(<RadioButtonInput value={value} stringOptions={options} onChange={spy} />);
    wrapper.find('input[type="radio"][value="c"]').simulate('change', { target: { value: 'c' } });
    expect(spy).toHaveBeenCalledTimes(1);
  });

  describe('defaultValue prop', () => {
    it('causes the onChange handler to be called with a default value when no value is set', () => {
      const value = undefined as string;
      const options = ['a', 'b', 'c', 'd'];
      const spy = jasmine.createSpy('onChange');
      mount(<RadioButtonInput value={value} defaultValue={options[0]} stringOptions={options} onChange={spy} />);
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy.calls.mostRecent().args[0].target.value).toBe('a');
    });

    it('causes the onChange handler to be called with a default value when an invalid value is set', () => {
      const value = 'x';
      const options = ['a', 'b', 'c', 'd'];
      const spy = jasmine.createSpy('onChange');
      mount(<RadioButtonInput value={value} defaultValue={options[0]} stringOptions={options} onChange={spy} />);
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy.calls.mostRecent().args[0].target.value).toBe('a');
    });

    it('does not call the onChange handler if no defaultValue is provided', () => {
      const value = 'x';
      const options = ['a', 'b', 'c', 'd'];
      const spy = jasmine.createSpy('onChange');
      mount(<RadioButtonInput value={value} stringOptions={options} onChange={spy} />);
      expect(spy).not.toHaveBeenCalled();
    });
  });
});
