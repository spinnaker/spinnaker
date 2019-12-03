import React from 'react';
import { $rootScope } from 'ngimport';

import { ReactInjector } from 'core/reactShims';
import { Tooltip } from 'core/presentation';

export class FilterCollapse extends React.Component<{}> {
  private onClick = (pin: boolean) => {
    ReactInjector.insightFilterStateModel.pinFilters(pin);
    $rootScope.$apply(); // insight layout needs to change
    this.setState({}); // force re-render since we are using insight filter state model to show the collapse button
  };

  public render() {
    return (
      <>
        <h3 className="filters-placeholder">
          <Tooltip value="Show filters">
            <a className="btn btn-xs btn-default pin clickable" onClick={() => this.onClick(true)}>
              <i className="fa fa-forward" />
            </a>
          </Tooltip>
        </h3>
        <Tooltip value="Hide filters">
          <a
            className="btn btn-xs btn-default pull-right unpin clickable"
            onClick={() => this.onClick(false)}
            style={{ display: ReactInjector.insightFilterStateModel.filtersExpanded ? 'inherit' : 'none' }}
          >
            <i className="fa fa-backward" />
          </a>
        </Tooltip>
      </>
    );
  }
}
