import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { Search } from './Search';
import { SearchFilterTypeRegistry } from './SearchFilterTypeRegistry';

describe('<Search/>', () => {
  SearchFilterTypeRegistry.register({ key: 'account', name: 'Account' });
  SearchFilterTypeRegistry.register({ key: 'region', name: 'Region' });
  function getNewSearch(params: object, handleChange: () => void) {
    return render(<Search params={params} onChange={handleChange} />);
  }

  function noop(): void {}

  it('should display a search component with no tags', () => {
    const { container } = getNewSearch({}, noop);
    expect(container.querySelectorAll('div.tag')).toHaveLength(0);
  });

  it('should display a search component with existing tags', () => {
    const params = { name: 'test', region: 'us-west-1', account: 'prod' };
    const { container } = getNewSearch(params, noop);
    expect(container.querySelectorAll('div.tag')).toHaveLength(3);
  });

  it('should have focus when rendered and removed when blurred', () => {
    const { container } = getNewSearch({}, noop);
    const input = screen.getByRole('textbox');
    expect(container.querySelector('.search__input')).toHaveClass('search__input--focus');
    fireEvent.blur(input);
    expect(container.querySelector('.search__input')).toHaveClass('search__input--blur');
  });

  it('should clear the tags when the clear button is clicked', () => {
    let changeCalled = false;
    function handleChange() {
      changeCalled = true;
    }

    const params = { name: 'test', region: 'us-west-1', account: 'prod' };
    const { container } = getNewSearch(params, handleChange);
    fireEvent.click(screen.getByRole('button', { name: 'Clear search' }));
    expect(container.querySelectorAll('div.tag')).toHaveLength(0);
    expect(changeCalled).toBeTruthy();
  });
});
