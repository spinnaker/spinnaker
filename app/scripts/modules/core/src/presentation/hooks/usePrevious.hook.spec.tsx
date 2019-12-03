import React from 'react';
import { mount } from 'enzyme';
import { usePrevious } from './usePrevious.hook';

const TestComponent = ({ value, valueCallback }: { value: any; valueCallback: (value: any) => any }) => {
  const previousValue = usePrevious(value);
  valueCallback(previousValue);
  return null as JSX.Element;
};

describe('usePrevious', () => {
  it('should give back undefined on initial mount', () => {
    let valueFromHook: any;
    const valueCallback = (value: any) => (valueFromHook = value);
    mount(<TestComponent value={'first value'} valueCallback={valueCallback} />);

    expect(valueFromHook).toBe(undefined);
  });

  it('should give back the value before the most recent render', () => {
    let valueFromHook: any;
    const valueCallback = (value: any) => (valueFromHook = value);
    const component = mount(<TestComponent value={'first value'} valueCallback={valueCallback} />);

    component.setProps({ value: 'second value' });

    expect(valueFromHook).toBe('first value');

    component.setProps({ value: 'third value' });

    expect(valueFromHook).toBe('second value');
  });
});
