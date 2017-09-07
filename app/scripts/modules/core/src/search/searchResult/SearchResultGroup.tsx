import * as React from 'react';
import * as classNames from 'classnames';
import autoBindMethods from 'class-autobind-decorator';

export interface ISearchResultGroup {
  category: string;
  count: number;
  iconClass: string;
  name: string;
  order: number;
  results: any[];
}

export interface ISearchResultGroupProps {
  isActive: boolean;
  searchResultGroup: ISearchResultGroup,
  onClick?: (group: ISearchResultGroup) => void;
}

@autoBindMethods
export class SearchResultGroup extends React.Component<ISearchResultGroupProps> {

  public static defaultProps: Partial<ISearchResultGroupProps> = {
    onClick: () => {}
  };

  private handleClick(): void {
    const { searchResultGroup, onClick } = this.props;
    if (searchResultGroup.count > 0) {
      onClick(searchResultGroup);
    }
  }

  public render(): React.ReactElement<SearchResultGroup> {
    const { isActive, searchResultGroup } = this.props;
    const { count, iconClass, name } = searchResultGroup;
    const className = classNames({
      'search-group': true,
      'search-group--focus': isActive,
      'search-group--blur': !isActive,
      'faded': count === 0
    });

    return (
      <div className={className} onClick={this.handleClick}>
        <span className={`search-group-icon ${iconClass}`}/>
        <div className="search-group-name">{name}</div>
        <div className="badge">{count}</div>
      </div>
    );
  }
}
