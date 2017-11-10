import * as React from 'react';
import * as classNames from 'classnames';
import { BindAll } from 'lodash-decorators';

import { SearchService } from 'core/search/search.service';
import { ISearchResultType } from 'core';

export interface ISearchResultTabProps {
  type: ISearchResultType;
  resultsCount: number;
  isActive: boolean;
  onClick: (group: ISearchResultType) => void;
  iconClass?: string;
  label?: string;
}

@BindAll()
export class DefaultSearchResultTab extends React.Component<ISearchResultTabProps> {
  private handleClick(): void {
    const { type, resultsCount, onClick } = this.props;
    resultsCount && onClick && onClick(type);
  }

  public render() {
    const { isActive, type, resultsCount } = this.props;
    const iconClass = type.iconClass;
    const countLabel = resultsCount < SearchService.DEFAULT_PAGE_SIZE ? `${resultsCount}` : `${resultsCount}+`;

    const className = classNames({
      'search-group': true,
      'search-group--focus': isActive,
      'search-group--blur': !isActive,
      'faded': resultsCount === 0
    });

    return (
      <div className={className} onClick={this.handleClick}>
        <span className={`search-group-icon ${iconClass}`}/>
        <div className="search-group-name">{type.displayName}</div>
        <div className="badge">{countLabel}</div>
      </div>
    );
  }
}
