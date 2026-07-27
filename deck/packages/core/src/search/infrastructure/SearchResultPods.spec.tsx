import { shallow } from 'enzyme';
import React from 'react';

import type { ISearchResultPodData } from './SearchResultPods';
import { SearchResultPods } from './SearchResultPods';

describe('SearchResultPods', () => {
  it('does not render a nested Bootstrap container', () => {
    const results: ISearchResultPodData[] = [
      {
        category: 'applications',
        config: {} as any,
        results: [{ id: 'app', displayName: 'app', params: {}, extraData: {} } as any],
      },
    ];

    const wrapper = shallow(
      <SearchResultPods results={results} onRemoveItem={jasmine.createSpy()} onResultClick={jasmine.createSpy()} />,
    );

    expect(wrapper.find('.infrastructure-section').hasClass('container')).toBe(false);
  });
});
