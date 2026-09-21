import { mount } from 'enzyme';
import React from 'react';
import { Creatable } from 'react-select';

import { ReactSelectInput } from './ReactSelectInput';

const noop = () => {};

describe('<ReactSelectInput />', () => {
  describe('CREATABLE mode', () => {
    it('renders when used as a single-value select with a string value not present in options', () => {
      // Regression test: single-select (non-multi) CREATABLE usage passes a plain string as
      // `value`, not an array. This previously crashed with "value.filter is not a function"
      // because CreatableSelect assumed `value` was always an array.
      const render = () =>
        mount(
          <ReactSelectInput
            name="account-type"
            mode="CREATABLE"
            stringOptions={['kubernetes', 'aws']}
            value="some-custom-type"
            onChange={noop}
            clearable={false}
          />,
        );
      expect(render).not.toThrow();
    });

    it('includes a created value not present in stringOptions among the rendered options', () => {
      const wrapper = mount(
        <ReactSelectInput
          name="account-type"
          mode="CREATABLE"
          stringOptions={['kubernetes', 'aws']}
          value="some-custom-type"
          onChange={noop}
          clearable={false}
        />,
      );
      const values = wrapper.find(Creatable).prop('options') as Array<{ value: string }>;
      expect(values.map((o) => o.value)).toContain('some-custom-type');
    });

    it('renders without a value', () => {
      const render = () =>
        mount(
          <ReactSelectInput
            name="account-type"
            mode="CREATABLE"
            stringOptions={['kubernetes', 'aws']}
            value={undefined}
            onChange={noop}
            clearable={false}
          />,
        );
      expect(render).not.toThrow();
    });

    it('still works for multi-select usage with an array value', () => {
      const wrapper = mount(
        <ReactSelectInput
          name="account-types"
          mode="CREATABLE"
          multi={true}
          stringOptions={['kubernetes', 'aws']}
          value={['kubernetes', 'some-custom-type']}
          onChange={noop}
          clearable={false}
        />,
      );
      const values = wrapper.find(Creatable).prop('options') as Array<{ value: string }>;
      expect(values.map((o) => o.value)).toContain('some-custom-type');
    });
  });
});
