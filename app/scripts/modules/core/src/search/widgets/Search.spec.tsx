import * as React from 'react';
import { ReactWrapper, mount } from 'enzyme';

import { SearchFilterTypeRegistry } from './SearchFilterTypeRegistry';
import { ISearchProps, Search } from './Search';

describe('<Search/>', () => {

  SearchFilterTypeRegistry.register({ key: 'account', modifier: 'acct', text: 'Account' });
  SearchFilterTypeRegistry.register({ key: 'region', modifier: 'reg', text: 'Region' });
  let component: ReactWrapper<ISearchProps, any>;

  function getNewSearch(query: string, handleChange: () => void): ReactWrapper<ISearchProps, any> {
    return mount(<Search query={query} onChange={handleChange} />);
  }

  function noop(): void {}

  it('should display a search component with no tags', () => {
    component = getNewSearch('', noop);
    expect(component.find('div.tag').length).toBe(0);
  });

  it('should display a search component with existing tags', () => {
    const query = 'name=test&region=us-west-1&account=prod';
    component = getNewSearch(query, noop);
    expect(component.find('div.tag').length).toBe(3);
  });

  it('should have focus when rendered and removed when blurred', () => {
    component = getNewSearch('', noop);
    expect(component.find('div.search__input').hasClass('search__input--focus')).toBeTruthy();
    expect(component.find('div.search__input').hasClass('search__input--blur')).toBeFalsy();

    component.setState({ isFocused: false });
    expect(component.find('div.search__input').hasClass('search__input--focus')).toBeFalsy();
    expect(component.find('div.search__input').hasClass('search__input--blur')).toBeTruthy();
  });

  it('should clear the tags when the clear button is clicked', () => {

    let changeCalled = false;
    function handleChange() {
      changeCalled = true;
    }

    const query = 'name=test&region=us-west-1&account=prod';
    component = getNewSearch(query, handleChange);
    component.find('i.fa-times').simulate('click');
    expect(component.find('div.tag').length).toBe(0);
    expect(changeCalled).toBeTruthy();
  });
});
