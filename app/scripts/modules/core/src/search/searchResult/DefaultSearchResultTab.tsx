import React from 'react';

import { SearchStatus } from './SearchResults';
import { Tooltip } from '../../presentation';
import { SearchService } from '../search.service';
import { ISearchResultTabProps } from './searchResultType';
import { Spinner } from '../../widgets';

export class DefaultSearchResultTab extends React.Component<ISearchResultTabProps<any>> {
  public render() {
    const { isActive, resultSet } = this.props;
    const { type, results, status, error } = resultSet;

    const iconClass = type.iconClass;
    const resultsCount = results.length;
    const countLabel = resultsCount < SearchService.DEFAULT_PAGE_SIZE ? `${resultsCount}` : `${resultsCount}+`;

    const Badge = () => {
      switch (status) {
        case SearchStatus.SEARCHING:
          return <Spinner size="small" />;

        case SearchStatus.ERROR:
          return (
            <Tooltip value={error && error.toString()}>
              <i className="fa fa-exclamation-triangle" />
            </Tooltip>
          );

        default:
          if (results.length) {
            return <div className="badge">{countLabel}</div>;
          }

          return <div className="badge faded">{countLabel}</div>;
      }
    };

    const focusOrBlurClass = isActive ? 'search-group--focus' : 'search-group--blur';

    return (
      <div className={`flex-container-h baseline search-group ${focusOrBlurClass}`}>
        <span className={`flex-nogrow search-group-icon ${iconClass}`} />
        <div className="flex-grow search-group-name">{type.displayName}</div>
        <div className="flex-nogrow">
          <Badge />
        </div>
      </div>
    );
  }
}
