import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { Application } from 'core/application';
import { NgReact, ReactInjector } from 'core/reactShims';
import { Refresher } from 'core/presentation/refresher/Refresher';
import { Tooltip } from 'core/presentation/Tooltip';
import { UIView } from '@uirouter/react';
import { relativeTime, timestamp } from 'core/utils/timeFormatters';

import './application.less';

export interface IApplicationComponentProps {
  app: Application;
}

export interface IApplicationComponentState {
  compactHeader: boolean;
}

@autoBindMethods
export class ApplicationComponent extends React.Component<IApplicationComponentProps, IApplicationComponentState> {
  constructor(props: IApplicationComponentProps) {
    super(props);
    if (props.app.notFound) {
      ReactInjector.recentHistoryService.removeLastItem('applications');
      return;
    }

    this.state = {
      compactHeader: props.app.name.length > 20
    };

    props.app.enableAutoRefresh();
  }

  public componentWillUnmount(): void {
    if (!this.props.app.notFound) {
      this.props.app.disableAutoRefresh();
    }
  }

  public pageApplicationOwner(): void {
    ReactInjector.modalService.open({
      templateUrl: require('./modal/pageApplicationOwner.html'),
      controller: 'PageApplicationOwner as ctrl',
      resolve: {
        application: () => this.props.app
      }
    })
  }

  public handleRefresh(): void {
    this.props.app.refresh(true);
  }

  public render() {
    const { ApplicationNav, ApplicationNavSecondary } = NgReact;
    const refresherState = this.props.app.activeState || this.props.app;
    const RefresherTooltip = (
      <span>
        {refresherState.refreshing && <p>Application is <strong>refreshing</strong>.</p>}
        {!refresherState.refreshing && <p>(click <span className="fa fa-refresh"/> to refresh)</p>}
        <p>Last refresh: {timestamp(refresherState.lastRefresh)} <br/> ({relativeTime(refresherState.lastRefresh)})</p>
        <p className="small"><strong>Note:</strong> Due to caching, data may be delayed up to 2 minutes</p>
      </span>
    );

    const NotFound = this.props.app.notFound ? (
      <div>
        <h2 className="text-center">Application Not Found</h2>
        <p className="text-center" style={{marginBottom: '20px'}}>Please check your URL - we can't find any data for <em>{this.props.app.name}</em>.</p>
      </div>
    ) : null;

    const Found = !this.props.app.notFound ? (
      <h2>
        <i className="fa fa-window-maximize"/>
        <span className="application-name">{this.props.app.name}</span>
        <Refresher
          state={refresherState}
          tooltipTemplate={RefresherTooltip}
          refresh={this.handleRefresh}
        />
      </h2>
    ) : null;

    const PagerDutyButton = this.props.app.attributes.pdApiKey ? (
      <div className="page-button">
        <Tooltip value="Page application owner">
          <button
            className="btn btn-xs btn-danger btn-page-owner"
            onClick={this.pageApplicationOwner}
          >
            <i className="fa fa-phone"/>
          </button>
        </Tooltip>
      </div>
    ) : null;

    return (
      <div className="application">
        <div className={`page-header ${this.state.compactHeader ? 'compact-header' : ''}`}>
          {NotFound}
          <div className="container application-header">
            {Found}
            <div className="application-navigation">
              <ApplicationNav application={this.props.app}/>
              <div className="header-right">
                <ApplicationNavSecondary application={this.props.app}/>
                {PagerDutyButton}
              </div>
            </div>
          </div>
        </div>
        <div className="container scrollable-columns">
          <UIView className="secondary-panel" name="insight"/>
        </div>
      </div>
    );
  }
}
