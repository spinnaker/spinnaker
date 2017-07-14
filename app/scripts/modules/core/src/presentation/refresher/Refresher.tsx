import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';
import { $window } from 'ngimport';

import { IScheduler } from 'core/scheduler/scheduler.factory';
import { ReactInjector } from 'core/reactShims';
import { Tooltip } from 'core/presentation';
import { relativeTime, timestamp } from 'core/utils/timeFormatters';

import './refresher.less';

export interface IRefresherProps {
  refreshing: boolean;
  lastRefresh: number;
  refresh: (e: React.MouseEvent<HTMLElement>) => void;
}

export interface IRefresherState {
  color: string;
  relativeTime: string;
}

@autoBindMethods
export class Refresher extends React.Component<IRefresherProps, IRefresherState> {
  private activeRefresher: IScheduler;
  private yellowAge = 2 * 60 * 1000; // 2 minutes
  private redAge = 5 * 60 * 1000; // 5 minutes

  constructor(props: IRefresherProps) {
    super(props);

    this.state = this.parseState(props);

    // Update the state on an interval to make sure the color and tooltip get updated
    const { schedulerFactory } = ReactInjector;
    this.activeRefresher = schedulerFactory.createScheduler(2000);
    this.activeRefresher.subscribe(() => {
      this.setState(this.parseState(this.props));
    });
  }

  public componentWillUnmount(): void {
    if (this.activeRefresher) {
      this.activeRefresher.unsubscribe();
    }
  }

  public parseState(props: IRefresherProps): IRefresherState {
    const lastRefresh = props.lastRefresh || 0;
    const age = new Date().getTime() - lastRefresh;

    const color = age < this.yellowAge ? 'young' :
      age < this.redAge ? 'old' : 'ancient';

    return {
      color,
      relativeTime: relativeTime(this.props.lastRefresh)
    };
  }

  public render() {
    const RefresherTooltip = (
      <span>
        {this.props.refreshing && <p>Application is <strong>refreshing</strong>.</p>}
        {!this.props.refreshing && <p>(click <span className="fa fa-refresh"/> to refresh)</p>}
        <p>Last refresh: {timestamp(this.props.lastRefresh)} <br/> ({this.state.relativeTime})</p>
        <p className="small"><strong>Note:</strong> Due to caching, data may be delayed up to 2 minutes</p>
      </span>
    );

    return (
      <Tooltip template={RefresherTooltip} placement={$window.innerWidth < 1100 ? 'right' : 'bottom'}>
        <a className="refresher clickable" onClick={this.props.refresh}>
          <span className={`fa fa-refresh refresh-${this.state.color} ${this.props.refreshing ? 'fa-spin' : ''}`}/>
        </a>
      </Tooltip>
    );
  }
}
