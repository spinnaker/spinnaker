import React from 'react';

import type { ISearchResultPodData } from './SearchResultPods';
import { SearchResultPods } from './SearchResultPods';
import { renderWithRouter } from '../../utils/testUtils/rtl';

describe('SearchResultPods', () => {
  it('does not render a nested Bootstrap container', () => {
    const results: ISearchResultPodData[] = [
      {
        category: 'applications',
        config: { state: 'home.applications.application' } as any,
        results: [
          {
            id: 'app',
            displayName: 'app',
            state: 'home.applications.application',
            params: {},
            extraData: {},
          } as any,
        ],
      },
    ];

    const { container } = renderWithRouter(
      <SearchResultPods results={results} onRemoveItem={vi.fn()} onResultClick={vi.fn()} />,
    );

    expect(container.querySelector('.infrastructure-section')).not.toHaveClass('container');
  });
});
