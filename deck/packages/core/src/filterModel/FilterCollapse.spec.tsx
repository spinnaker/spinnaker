import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { FilterCollapse } from './FilterCollapse';

describe('FilterCollapse', () => {
  it('renders and toggles solely from controlled expansion state', () => {
    const onToggle = vi.fn();
    const { container, rerender } = render(<FilterCollapse filtersExpanded={false} onToggle={onToggle} />);

    const button = screen.getByRole('button');
    expect(container.querySelector('.filters-hidden')).toBeInTheDocument();
    expect(button).toHaveAttribute('type', 'button');
    expect(button).toHaveClass('pin');
    expect(container.querySelector('.fa-forward')).toBeInTheDocument();
    expect(container.querySelector('.show-filter-text')).toHaveTextContent('Show filters');

    fireEvent.click(button);
    expect(onToggle).toHaveBeenCalledTimes(1);

    rerender(<FilterCollapse filtersExpanded={true} onToggle={onToggle} />);
    const expandedButton = screen.getByRole('button');
    expect(container.querySelector('.filters-open')).toBeInTheDocument();
    expect(expandedButton).toHaveClass('unpin');
    expect(container.querySelector('.fa-backward')).toBeInTheDocument();
    expect(container).toHaveTextContent('Filters');

    fireEvent.click(expandedButton);
    expect(onToggle).toHaveBeenCalledTimes(2);
  });
});
