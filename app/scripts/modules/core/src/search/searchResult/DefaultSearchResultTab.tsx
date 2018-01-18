import * as React from 'react';
import * as classNames from 'classnames';

import { Spinner } from 'core/widgets';
import { Tooltip } from 'core/presentation';

import { SearchService } from '../search.service';
import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { SearchStatus } from './SearchResults';

export interface ISearchResultTabProps {
  resultSet: ISearchResultSet;
  isActive: boolean;
}

export class DefaultSearchResultTab extends React.Component<ISearchResultTabProps> {
  public render() {
    const { isActive, resultSet } = this.props;
    const { type, results, status, error } = resultSet;

    const iconClass = type.iconClass;
    const resultsCount = results.length;
    const countLabel = resultsCount < SearchService.DEFAULT_PAGE_SIZE ? `${resultsCount}` : `${resultsCount}+`;

    const className = classNames({
      'search-group': true,
      'search-group--focus': isActive,
      'search-group--blur': !isActive,
    });

    const Badge = () => {
      switch (status) {
        case SearchStatus.SEARCHING:
          return <Spinner size="small"/>;

        case SearchStatus.ERROR:
          return (
            <Tooltip value={error && error.toString()}>
              <i className="fa fa-exclamation-triangle"/>
            </Tooltip>
          );

        default:
          if (results.length) {
            return <div className="badge">{countLabel}</div>;
          }

          return <div className="badge faded">{countLabel}</div>
      }
    };

    return (
      <div className={className}>
        <span className={`search-group-icon ${iconClass}`}/>
        <div className="search-group-name">{type.displayName}</div>
        <Badge/>
      </div>
    );
  }
}
