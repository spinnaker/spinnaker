import React from 'react';
import { $rootScope } from 'ngimport';

import { ReactInjector } from 'core/reactShims';
import { Tooltip } from 'core/presentation';
import './FilterCollapse.less';

export class FilterCollapse extends React.Component<{}> {
  private onClick = (pin: boolean) => {
    ReactInjector.insightFilterStateModel.pinFilters(pin);
    $rootScope.$apply(); // insight layout needs to change
    this.setState({}); // force re-render since we are using insight filter state model to show the collapse button
  };

  public render() {
    return (
      <div className="filters-toggle layer-high sp-margin-s-xaxis">
        <h3 className="filters-placeholder">
          <Tooltip value="Show filters">
            <button
              className="btn btn-xs btn-default pin clickable"
              onClick={() => this.onClick(true)}
              style={{ display: ReactInjector.insightFilterStateModel.filtersExpanded ? 'none' : 'inherit' }}
            >
              <i className="fa fa-forward" />
              <span className="show-filter-text"> Show filters</span>
            </button>
          </Tooltip>
        </h3>
        <h3 className="filters-placeholder">
          <Tooltip value="Hide filters">
            <button
              className="btn btn-xs btn-default pull-left unpin clickable"
              onClick={() => this.onClick(false)}
              style={{ display: ReactInjector.insightFilterStateModel.filtersExpanded ? 'inherit' : 'none' }}
            >
              <i className="fa fa-backward" />
            </button>
          </Tooltip>
        </h3>
      </div>
    );
  }
}
