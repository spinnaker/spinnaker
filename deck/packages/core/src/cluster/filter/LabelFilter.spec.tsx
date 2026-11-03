import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import LabelFilter, {
  getLabelKeyOptions,
  getLabelValueOptions,
  updateLabelFilterKey,
  updateLabelFilterValue,
} from './LabelFilter';

describe('LabelFilter', () => {
  const labelsMap = { app: ['deck', 'orca'], env: ['test', 'prod'], team: ['delivery'] };

  it('offers unused keys and values for the selected key', () => {
    const filters = [
      { key: 'app', value: 'deck' },
      { key: 'env', value: 'test' },
    ];

    expect(getLabelKeyOptions(labelsMap, filters, 0)).toEqual([
      { label: 'app', value: 'app' },
      { label: 'team', value: 'team' },
    ]);
    expect(getLabelValueOptions(labelsMap, 'env')).toEqual([
      { label: 'test', value: 'test' },
      { label: 'prod', value: 'prod' },
    ]);
  });

  it('replaces the selected key or value without changing other filters', () => {
    const filters = [
      { key: 'app', value: 'deck' },
      { key: 'env', value: 'test' },
    ];

    expect(updateLabelFilterKey(filters, 1, 'team')).toEqual([filters[0], { key: 'team', value: null }]);
    expect(updateLabelFilterValue(filters, 1, 'prod')).toEqual([filters[0], { key: 'env', value: 'prod' }]);
  });

  it('adds one empty filter and does not add another while it is incomplete', () => {
    const updateLabelFilters = vi.fn();
    const { rerender } = render(
      <LabelFilter labelsMap={labelsMap} labelFilters={[]} updateLabelFilters={updateLabelFilters} />,
    );

    fireEvent.click(screen.getByRole('button', { name: /Add label filter/ }));
    expect(updateLabelFilters).toHaveBeenCalledWith([{ key: null, value: null }]);

    updateLabelFilters.mockClear();
    rerender(
      <LabelFilter
        labelsMap={labelsMap}
        labelFilters={[{ key: null, value: null }]}
        updateLabelFilters={updateLabelFilters}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /Add label filter/ }));
    expect(updateLabelFilters).not.toHaveBeenCalled();
  });

  it('removes a filter through its visible delete button', () => {
    const updateLabelFilters = vi.fn();
    render(
      <LabelFilter
        labelsMap={labelsMap}
        labelFilters={[
          { key: 'app', value: 'deck' },
          { key: 'env', value: 'test' },
        ]}
        updateLabelFilters={updateLabelFilters}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Remove app label filter' }));
    expect(updateLabelFilters).toHaveBeenCalledWith([{ key: 'env', value: 'test' }]);
  });
});
