import { render } from '@testing-library/react';
import React from 'react';

import type { IFiltersLayout } from './Filters';
import { Filters } from './Filters';
import type { IFilterType } from './SearchFilterTypeRegistry';

describe('<Filters/>', () => {
  function getNewFilterType(seed: number): IFilterType {
    return {
      key: `filter-type-${seed}`,
      name: `Filter Type ${seed}`,
    };
  }

  function getNewLayout(seed: number): IFiltersLayout {
    return {
      header: `header_${seed}`,
      filterTypes: [1, 2, 3].map((s: number) => getNewFilterType(s)),
    };
  }

  function getNewFilters(isOpen: boolean) {
    const activeFilter = getNewFilterType(1);
    return render(
      <Filters
        activeFilter={activeFilter}
        layouts={[1, 2].map((seed: number) => getNewLayout(seed))}
        isOpen={isOpen}
      />,
    );
  }

  it('should render a list of filters', () => {
    expect(getNewFilters(true).container.firstChild).toHaveClass('filter-list');
  });

  it('should open the filter list when isOpen is true', () => {
    const { container } = getNewFilters(true);
    expect(container.firstChild).toHaveClass('filter-list', 'filter-list__open');
    expect(container.firstChild).not.toHaveClass('filter-list__closed');
  });

  it('should close the filter list when isOpen is false', () => {
    const { container } = getNewFilters(false);
    expect(container.firstChild).toHaveClass('filter-list', 'filter-list__closed');
    expect(container.firstChild).not.toHaveClass('filter-list__open');
  });
});
