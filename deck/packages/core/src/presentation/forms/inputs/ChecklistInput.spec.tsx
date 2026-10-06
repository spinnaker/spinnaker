import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { ChecklistInput } from './ChecklistInput';

const noop = () => {};
const options = ['a', 'b', 'c', 'd'];
const checkedOptions = ['a', 'b', 'c'];

describe('<ChecklistInput />', () => {
  it('initializes properly with provided values', () => {
    render(<ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} />);

    expect(screen.getAllByRole('checkbox')).toHaveLength(4);
    expect(screen.getAllByRole('checkbox').filter((checkbox) => (checkbox as HTMLInputElement).checked)).toHaveLength(
      3,
    );
  });

  it('updates items when options are added or removed externally', () => {
    const { rerender } = render(<ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} />);

    rerender(<ChecklistInput value={checkedOptions} stringOptions={options.concat('e')} onChange={noop} />);
    expect(screen.getAllByRole('checkbox')).toHaveLength(5);

    rerender(
      <ChecklistInput value={checkedOptions} stringOptions={options.filter((item) => item !== 'c')} onChange={noop} />,
    );
    expect(screen.getAllByRole('checkbox')).toHaveLength(3);
    expect(screen.getAllByRole('checkbox').filter((checkbox) => (checkbox as HTMLInputElement).checked)).toHaveLength(
      2,
    );
  });

  it('updates checked items when values change externally', () => {
    const { rerender } = render(<ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} />);

    rerender(<ChecklistInput value={options} stringOptions={options} onChange={noop} />);
    expect(screen.getAllByRole('checkbox').every((checkbox) => (checkbox as HTMLInputElement).checked)).toBe(true);

    rerender(<ChecklistInput value={['a', 'b']} stringOptions={options} onChange={noop} />);
    expect(screen.getAllByRole('checkbox').filter((checkbox) => (checkbox as HTMLInputElement).checked)).toHaveLength(
      2,
    );
  });

  it('returns the exact values when an individual checkbox is selected', async () => {
    const onChange = vi.fn();
    render(<ChecklistInput value={['a']} stringOptions={options} onChange={onChange} />);

    await userEvent.click(screen.getByRole('checkbox', { name: 'c' }));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange.mock.lastCall[0].target.value).toEqual(['a', 'c']);
  });

  it('shows the bulk selection button only when requested', () => {
    const { rerender } = render(
      <ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} showSelectAll={true} />,
    );
    expect(screen.getByRole('button', { name: 'Select All' })).toBeInTheDocument();

    rerender(<ChecklistInput value={checkedOptions} stringOptions={options} onChange={noop} showSelectAll={false} />);
    expect(screen.queryByRole('button', { name: 'Select All' })).not.toBeInTheDocument();
  });

  it('selects and deselects all values', async () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <ChecklistInput value={['a']} stringOptions={options} onChange={onChange} showSelectAll={true} />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Select All' }));
    expect(onChange.mock.lastCall[0].target.value).toEqual(options);

    rerender(<ChecklistInput value={options} stringOptions={options} onChange={onChange} showSelectAll={true} />);
    await userEvent.click(screen.getByRole('button', { name: 'Deselect All' }));
    expect(onChange.mock.lastCall[0].target.value).toEqual([]);
  });

  it('preserves the inline appearance of the previous anchor control', () => {
    render(
      <>
        <div className="ChecklistInput ChecklistInput_inline">
          <ul>
            <li>
              <a type="button">Previous anchor</a>
            </li>
          </ul>
        </div>
        <ChecklistInput
          inline={true}
          value={checkedOptions}
          stringOptions={options}
          onChange={noop}
          showSelectAll={true}
        />
      </>,
    );

    const previousStyle = window.getComputedStyle(screen.getByText('Previous anchor'));
    const buttonStyle = window.getComputedStyle(screen.getByRole('button', { name: 'Select All' }));
    const parityProperties = [
      'backgroundColor',
      'borderTopStyle',
      'borderRightStyle',
      'borderBottomStyle',
      'borderLeftStyle',
      'color',
      'marginTop',
      'marginRight',
      'marginBottom',
      'marginLeft',
      'paddingTop',
      'paddingRight',
      'paddingBottom',
      'paddingLeft',
      'textDecorationLine',
    ] as const;

    const normalizeZero = (value: string) => (value === '0' ? '0px' : value);
    parityProperties.forEach((property) =>
      expect(normalizeZero(buttonStyle[property])).toBe(normalizeZero(previousStyle[property])),
    );
  });
});
