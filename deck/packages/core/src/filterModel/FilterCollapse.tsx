import React from 'react';

import { Tooltip } from '../presentation';

import './FilterCollapse.less';

export interface IFilterCollapseProps {
  filtersExpanded: boolean;
  onToggle: () => void;
}

export class FilterCollapse extends React.Component<IFilterCollapseProps> {
  public render() {
    const { filtersExpanded, onToggle } = this.props;

    return (
      <div className="filters-toggle layer-medium">
        {!filtersExpanded && (
          <div className="filters-placeholder filters-hidden">
            <Tooltip value="Show filters">
              <button
                className="btn btn-xs btn-default pin clickable sp-padding-xs"
                onClick={onToggle}
                style={{ display: filtersExpanded ? 'none' : 'inherit' }}
              >
                <i className="fa fa-forward" />
                <span className="show-filter-text"> Show filters</span>
              </button>
            </Tooltip>
          </div>
        )}
        {filtersExpanded && (
          <div className="filters-placeholder filters-open horizontal middle">
            <Tooltip value="Hide filters">
              <button
                className="btn btn-xs btn-default unpin clickable sp-margin-s-xaxis sp-margin-2xs-yaxis sp-padding-xs"
                onClick={onToggle}
                style={{ display: filtersExpanded ? 'inherit' : 'none' }}
              >
                <i className="fa fa-backward" />
              </button>
            </Tooltip>
            <div className="horizontal center flex-1 sp-margin-xl-right">Filters</div>
          </div>
        )}
      </div>
    );
  }
}
