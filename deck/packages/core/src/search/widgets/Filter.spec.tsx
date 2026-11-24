import { render, screen } from '@testing-library/react';
import React from 'react';

import { Filter } from './Filter';
import type { IFilterType } from './SearchFilterTypeRegistry';

describe('<Filter/>', () => {
  function getNewFilterType(): IFilterType {
    return {
      key: 'region',
      name: 'Region',
    };
  }

  it('should display a filter', () => {
    const filterType: IFilterType = getNewFilterType();
    const { container } = render(<Filter filterType={filterType} isActive={true} />);

    expect(container.firstChild).toHaveClass('filter');
    expect(screen.getByText(filterType.name)).toHaveClass('filter__text');
    expect(screen.getByText(`[${filterType.key.toLocaleUpperCase()}:]`)).toHaveClass('filter__modifier');
  });

  it('should set the tab focus class when active', () => {
    const { container } = render(<Filter filterType={getNewFilterType()} isActive={true} />);
    expect(container.firstChild).toHaveClass('filter', 'filter--focus');
    expect(container.firstChild).not.toHaveClass('filter--blur');
  });

  it('should set the tab blur class when not active', () => {
    const { container } = render(<Filter filterType={getNewFilterType()} isActive={false} />);
    expect(container.firstChild).toHaveClass('filter', 'filter--blur');
    expect(container.firstChild).not.toHaveClass('filter--focus');
  });
});
