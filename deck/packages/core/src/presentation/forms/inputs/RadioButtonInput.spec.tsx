import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { RadioButtonInput } from './RadioButtonInput';

const noop = () => {};
const options = ['a', 'b', 'c', 'd'];

describe('<RadioButtonInput />', () => {
  it('renders radio button inputs', () => {
    render(<RadioButtonInput value="b" stringOptions={options} onChange={noop} />);

    expect(screen.getAllByRole('radio')).toHaveLength(4);
  });

  it('updates the selected item using the value prop', () => {
    const { rerender } = render(<RadioButtonInput value="b" stringOptions={options} onChange={noop} />);
    expect(screen.getByRole('radio', { name: 'b' })).toBeChecked();

    rerender(<RadioButtonInput value="c" stringOptions={options} onChange={noop} />);

    expect(screen.getByRole('radio', { name: 'c' })).toBeChecked();
  });

  it('wires the onChange handler to the radios', async () => {
    const values: string[] = [];
    const onChange = vi.fn((event) => values.push(event.target.value));
    render(<RadioButtonInput value="b" stringOptions={options} onChange={onChange} />);

    await userEvent.click(screen.getByRole('radio', { name: 'c' }));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(values).toEqual(['c']);
  });

  describe('defaultValue prop', () => {
    it.each([undefined, 'x'])('uses the default value when the current value is %s', (value) => {
      const onChange = vi.fn();
      render(<RadioButtonInput value={value} defaultValue="a" stringOptions={options} onChange={onChange} />);

      expect(onChange).toHaveBeenCalledTimes(1);
      expect(onChange.mock.lastCall[0].target.value).toBe('a');
    });

    it('does not call the onChange handler if no defaultValue is provided', () => {
      const onChange = vi.fn();
      render(<RadioButtonInput value="x" stringOptions={options} onChange={onChange} />);

      expect(onChange).not.toHaveBeenCalled();
    });
  });
});
