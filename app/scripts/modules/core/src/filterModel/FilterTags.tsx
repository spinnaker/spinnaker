import * as React from 'react';
import * as ReactGA from 'react-ga';

export interface IFilter {
  label: string;
  value: string;
}

export interface IFilterTag extends IFilter {
  clear: () => any;
  key: string;
}

export interface IFilterTagsProps {
  tags: IFilterTag[];
  tagCleared?: () => any;
  clearFilters: () => any;
}

export interface IFilterTagsState {
  tags: IFilterTag[];
}

export class FilterTags extends React.Component<IFilterTagsProps, IFilterTagsState> {
  public static defaultProps: Partial<IFilterTagsProps> = {
    tagCleared: () => {},
  };

  constructor(props: IFilterTagsProps) {
    super(props);
    this.state = {
      tags: props.tags,
    };
  }

  public componentWillReceiveProps(newProps: IFilterTagsProps) {
    this.setState({ tags: newProps.tags });
  }

  private clearAllFilters = (): void => {
    this.props.clearFilters();
    ReactGA.event({ category: 'Filter Tags', action: 'Clear All clicked' });
  };

  private generateTag(tag: IFilterTag) {
    const clearFilter = (): void => {
      tag.clear();
      this.props.tagCleared();
      ReactGA.event({ category: 'Filter Tags', action: 'Individual tag removed' });
    };
    return (
      <span className="filter-tag" key={[tag.label, tag.value].join(':')}>
        <strong>{tag.label}</strong>: {tag.value}
        <a className="clickable clear-filters" onClick={clearFilter}>
          <span className="glyphicon glyphicon-remove-sign" />
        </a>
      </span>
    );
  }

  public render() {
    const { tags } = this.state;
    return (
      <div className="col-md-12 filter-tags">
        {tags && tags.length > 0 && (
          <span>
            <span>Filtered by: </span>
            {tags.map(tag => this.generateTag(tag))}
            {tags.length > 1 && (
              <a className="clickable clear-filters" onClick={this.clearAllFilters}>
                Clear All
              </a>
            )}
          </span>
        )}
      </div>
    );
  }
}
