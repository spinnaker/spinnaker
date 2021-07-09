import { kebabCase } from 'lodash';
import React from 'react';

import { SearchStatus } from './SearchResults';
import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { Spinner } from '../../widgets';

export interface ISearchResultGridProps {
  resultSet: ISearchResultSet;
}

export const Searching = () => (
  <div className="flex-grow vertical center middle">
    <Spinner size="large" message="Fetching search results ..." />
  </div>
);

const NoResults = ({ resultSet }: { resultSet: ISearchResultSet }) => (
  <div className="flex-grow vertical center middle">
    <h3>No {resultSet.type.displayName} found for the specified search query</h3>
  </div>
);

const Results = ({ resultSet }: { resultSet: ISearchResultSet }) => {
  const { type } = resultSet;
  const { HeaderComponent, DataComponent } = type;

  return (
    <div className="search-result-grid flex-fill" style={{ height: 'initial' }}>
      <div className={`table table-search-results table-search-results-${kebabCase(type.id)}`}>
        <HeaderComponent resultSet={resultSet} />
        <DataComponent resultSet={resultSet} />
      </div>
    </div>
  );
};

const FetchError = ({ resultSet }: { resultSet: ISearchResultSet }) => (
  <div className="flex-grow vertical center middle">
    <h4 className="error-message">Could not fetch {resultSet.type.displayName}</h4>
    <div className="error-message">{resultSet && resultSet.error && resultSet.error.toString()}</div>
  </div>
);

export class SearchResultGrid extends React.Component<ISearchResultGridProps> {
  public render(): React.ReactElement<SearchResultGrid> {
    const resultSet = this.props.resultSet || ({} as ISearchResultSet);
    const { status, results } = resultSet;

    switch (status) {
      case SearchStatus.INITIAL:
      case undefined:
        return null;

      case SearchStatus.ERROR:
        return <FetchError resultSet={resultSet} />;

      case SearchStatus.SEARCHING:
        return <Searching />;

      case SearchStatus.FINISHED:
        if (!results.length) {
          return <NoResults resultSet={resultSet} />;
        }

        return <Results resultSet={resultSet} />;
      default:
        return null;
    }
  }
}
