import { render, screen } from '@testing-library/react';
import React from 'react';

import { ReactSelectInput } from './ReactSelectInput';
import { setupUser } from '../../../utils/testUtils';

const noop = () => {};

describe('<ReactSelectInput />', () => {
  describe('CREATABLE mode', () => {
    it('renders when used as a single-value select with a string value not present in options', () => {
      // Regression test: single-select (non-multi) CREATABLE usage passes a plain string as
      // `value`, not an array. This previously crashed with "value.filter is not a function"
      // because CreatableSelect assumed `value` was always an array.
      const renderInput = () =>
        render(
          <ReactSelectInput
            name="account-type"
            mode="CREATABLE"
            stringOptions={['kubernetes', 'aws']}
            value="some-custom-type"
            onChange={noop}
            clearable={false}
          />,
        );
      expect(renderInput).not.toThrow();
    });

    it('includes a created value not present in stringOptions among the rendered options', async () => {
      const user = setupUser();
      render(
        <ReactSelectInput
          name="account-type"
          mode="CREATABLE"
          stringOptions={['kubernetes', 'aws']}
          value="some-custom-type"
          onChange={noop}
          clearable={false}
        />,
      );

      // react-select 1.x only renders a value that is present in its options
      expect(screen.getByText('some-custom-type', { selector: '.Select-value-label' })).toBeInTheDocument();

      await user.click(screen.getByRole('combobox'));
      await user.keyboard('{ArrowDown}');
      const options = screen.getAllByRole('option').map((o) => o.textContent);
      expect(options).toContain('some-custom-type');
    });

    it('renders without a value', () => {
      const renderInput = () =>
        render(
          <ReactSelectInput
            name="account-type"
            mode="CREATABLE"
            stringOptions={['kubernetes', 'aws']}
            value={undefined}
            onChange={noop}
            clearable={false}
          />,
        );
      expect(renderInput).not.toThrow();
    });

    it('still works for multi-select usage with an array value', () => {
      const { container } = render(
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

      // react-select 1.x only renders values that are present in its options
      const values = Array.from(container.querySelectorAll('.Select-value-label')).map((v) => v.textContent.trim());
      expect(values).toContain('some-custom-type');
      expect(values).toContain('kubernetes');
    });
  });
});
