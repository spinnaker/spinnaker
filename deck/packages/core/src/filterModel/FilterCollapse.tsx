import { $rootScope } from 'ngimport';
import React from 'react';

import { Tooltip } from '../presentation';
import { ReactInjector } from '../reactShims';

import './FilterCollapse.less';

export class FilterCollapse extends React.Component<{}> {
  private onClick = (pin: boolean) => {
    ReactInjector.insightFilterStateModel.pinFilters(pin);
    $rootScope.$apply(); // insight layout needs to change
    this.setState({}); // force re-render since we are using insight filter state model to show the collapse button
  };

  public render() {
    const { filtersExpanded } = ReactInjector.insightFilterStateModel;

    return (
      <div className="filters-toggle layer-medium">
        {!filtersExpanded && (
          <div className="filters-placeholder filters-hidden">
            <Tooltip value="Show filters">
              <button
                className="btn btn-xs btn-default pin clickable sp-padding-xs"
                onClick={() => this.onClick(true)}
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
                onClick={() => this.onClick(false)}
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
