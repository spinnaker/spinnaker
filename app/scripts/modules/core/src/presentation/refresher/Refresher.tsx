import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';
import { $window } from 'ngimport';

import { Tooltip } from 'core/presentation';

import './refresher.less';

export interface IRefresherProps {
  state: {
    refreshing: boolean;
    lastRefresh: number;
  };
  tooltipTemplate: JSX.Element;
  refresh: (e: React.MouseEvent<HTMLElement>) => void;
}

@autoBindMethods
export class Refresher extends React.Component<IRefresherProps, {}> {
  public getAgeColor(): string {
    const yellowAge = 2 * 60 * 1000; // 2 minutes
    const redAge = 5 * 60 * 1000; // 5 minutes
    const lastRefresh = this.props.state.lastRefresh || 0;
    const age = new Date().getTime() - lastRefresh;

    return age < yellowAge ? 'young' :
      age < redAge ? 'old' : 'ancient';
  }

  public render() {
    return (
      <Tooltip template={this.props.tooltipTemplate} placement={$window.innerWidth < 1100 ? 'right' : 'bottom'}>
        <div>
          <a className="refresher clickable" onClick={this.props.refresh}>
            <span className={`fa fa-refresh refresh-${this.getAgeColor()} ${this.props.state.refreshing ? 'fa-spin' : ''}`}/>
          </a>
        </div>
      </Tooltip>
    );
  }
}
