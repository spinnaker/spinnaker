import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { SelectInput } from './SelectInput';

const noop = () => {};
const options = ['a', 'b', 'c', 'd'];

describe('<SelectInput />', () => {
  it('renders a select with options', () => {
    render(<SelectInput value="b" options={options} onChange={noop} />);

    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.getAllByRole('option')).toHaveLength(4);
  });

  it('updates the selected item using the value prop', () => {
    const { rerender } = render(<SelectInput value="b" options={options} onChange={noop} />);
    expect(screen.getByRole('combobox')).toHaveValue('b');

    rerender(<SelectInput value="c" options={options} onChange={noop} />);

    expect(screen.getByRole('combobox')).toHaveValue('c');
  });

  it('preserves the native select and focus when props change', () => {
    const { rerender } = render(<SelectInput value="a" options={['a', 'b']} onChange={noop} />);
    const select = screen.getByRole('combobox');
    select.focus();

    rerender(<SelectInput value="b" options={['a', 'b', 'c']} onChange={noop} />);

    expect(screen.getByRole('combobox')).toBe(select);
    expect(select).toHaveFocus();
  });

  it('wires the onChange handler to the selected item', async () => {
    const values: string[] = [];
    const onChange = vi.fn((event) => values.push(event.target.value));
    render(<SelectInput value="b" options={options} onChange={onChange} />);

    await userEvent.selectOptions(screen.getByRole('combobox'), 'c');

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(values).toEqual(['c']);
  });

  describe('defaultValue prop', () => {
    it.each([undefined, 'x'])('uses the default value when the current value is %s', (value) => {
      const onChange = vi.fn();
      render(<SelectInput value={value} defaultValue="a" options={options} onChange={onChange} />);

      expect(onChange).toHaveBeenCalledTimes(1);
      expect(onChange.mock.lastCall[0].target.value).toBe('a');
    });

    it('does not call the onChange handler if no defaultValue is provided', () => {
      const onChange = vi.fn();
      render(<SelectInput value="x" options={options} onChange={onChange} />);

      expect(onChange).not.toHaveBeenCalled();
    });
  });
});
