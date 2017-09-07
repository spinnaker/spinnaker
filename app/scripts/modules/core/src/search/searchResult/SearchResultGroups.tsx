import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { ISearchResultGroup, SearchResultGroup } from './SearchResultGroup';

export interface ISearchResultGroupsProps {
  activeSearchResult: ISearchResultGroup;
  onClick?: (group: ISearchResultGroup) => void;
  searchResultGroups: ISearchResultGroup[]
}

@autoBindMethods
export class SearchResultGroups extends React.Component<ISearchResultGroupsProps> {

  public static defaultProps: Partial<ISearchResultGroupsProps> = {
    onClick: () => {}
  };

  private handleClick(group: ISearchResultGroup): void {
    this.props.onClick(group);
  }

  private generateSearchResultGroupElement(group: ISearchResultGroup): React.ReactElement<SearchResultGroup> {
    const { activeSearchResult } = this.props;
    return (
      <SearchResultGroup
        key={group.name}
        isActive={activeSearchResult ? (activeSearchResult.name === group.name) : false}
        searchResultGroup={group}
        onClick={this.handleClick}
      />
    );
  }

  public render(): React.ReactElement<SearchResultGroups> {

    const groups = (this.props.searchResultGroups || [])
      .map((group: ISearchResultGroup) => this.generateSearchResultGroupElement(group));
    return (
      <div className="search-groups">{groups}</div>
    );
  }
}
