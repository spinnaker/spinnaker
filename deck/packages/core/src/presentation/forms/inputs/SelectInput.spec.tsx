import { mount } from 'enzyme';
import React from 'react';

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

  it('preserves the native select and focus when props change', () => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const wrapper = mount(<SelectInput value="a" options={['a', 'b']} onChange={noop} />, { attachTo: host });

    try {
      const select = wrapper.find('select').getDOMNode<HTMLSelectElement>();
      select.focus();

      wrapper.setProps({ value: 'b', options: ['a', 'b', 'c'] });

      expect(wrapper.find('select').getDOMNode<HTMLSelectElement>()).toBe(select);
      expect(document.activeElement).toBe(select);
    } finally {
      wrapper.unmount();
      host.remove();
    }
  });

  it('wires the onChange handler to the selected item', () => {
    const value = 'b';
    const options = ['a', 'b', 'c', 'd'];
    const spy = vi.fn();
    const component = mount(<SelectInput value={value} options={options} onChange={spy} />);
    component.find('select').simulate('change', { target: { value: 'c' } });
    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy.mock.lastCall[0].target.value).toBe('c');
  });

  describe('defaultValue prop', () => {
    it('causes the onChange handler to be called with a default value when no value is set', () => {
      const value = undefined as string;
      const options = ['a', 'b', 'c', 'd'];
      const spy = vi.fn();
      mount(<SelectInput value={value} defaultValue={options[0]} options={options} onChange={spy} />);
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy.mock.lastCall[0].target.value).toBe('a');
    });

    it('causes the onChange handler to be called with a default value when an invalid value is set', () => {
      const value = 'x';
      const options = ['a', 'b', 'c', 'd'];
      const spy = vi.fn();
      mount(<SelectInput value={value} defaultValue={options[0]} options={options} onChange={spy} />);
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy.mock.lastCall[0].target.value).toBe('a');
    });

    it('does not call the onChange handler if no defaultValue is provided', () => {
      const value = 'x';
      const options = ['a', 'b', 'c', 'd'];
      const spy = vi.fn();
      mount(<SelectInput value={value} options={options} onChange={spy} />);
      expect(spy).not.toHaveBeenCalled();
    });
  });
});
