import * as React from 'react';
import * as ReactGA from 'react-ga';
import autoBindMethods from 'class-autobind-decorator';

export interface IFilter {
  label: string;
  value: string;
}

export interface IFilterTag extends IFilter {
  clear: () => any;
}

export interface IProps {
  tags: IFilterTag[];
  tagCleared?: () => any;
  clearFilters: () => any;
}

export interface IState {
  tags: IFilterTag[];
}

@autoBindMethods
export class FilterTags extends React.Component<IProps, IState> {

  constructor(props: IProps) {
    super(props);
    this.state = {
      tags: props.tags
    };
  }

  public componentWillReceiveProps(newProps: IProps) {
    this.setState({ tags: newProps.tags });
  }

  private clearAllFilters(): void {
    this.props.clearFilters();
    ReactGA.event({category: 'Filter Tags', action: 'Clear All clicked'});
  }

  private generateTag(tag: IFilterTag) {
    const clearFilter = (): void => {
      tag.clear();
      if (this.props.tagCleared) {
        this.props.tagCleared();
      }
      ReactGA.event({category: 'Filter Tags', action: 'Individual tag removed'});
    };
    return (
      <span className="filter-tag" key={[tag.label, tag.value].join(':')}>
        <strong>{tag.label}</strong>: {tag.value}
        <a className="clickable clear-filters" onClick={clearFilter}>
          <span className="glyphicon glyphicon-remove-sign"/>
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
            {tags.length > 1 && (<a className="clickable clear-filters" onClick={this.clearAllFilters}>Clear All</a>)}
          </span>
        )}
      </div>
    );
  }
}
