import { ApplicationRefresher } from 'core/application/nav/ApplicationRefresher';
import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { UIView } from '@uirouter/react';

import { Application } from 'core/application';
import { NgReact, ReactInjector } from 'core/reactShims';
import { DebugWindow } from 'core/utils/consoleDebug';

import { ApplicationIcon } from './ApplicationIcon';
import './application.less';
import { PagerDutyButton } from './nav/PagerDutyButton';

export interface IApplicationComponentProps {
  app: Application;
}

export interface IApplicationComponentState {
  compactHeader: boolean;
}

@BindAll()
export class ApplicationComponent extends React.Component<IApplicationComponentProps, IApplicationComponentState> {

  constructor(props: IApplicationComponentProps) {
    super(props);
    const { app } = props;
    this.state = this.parseState(props);
    if (props.app.notFound) {
      ReactInjector.recentHistoryService.removeLastItem('applications');
      return;
    }

    DebugWindow.application = app;
    app.enableAutoRefresh();
  }

  private parseState(props: IApplicationComponentProps): IApplicationComponentState {
    return {
      compactHeader: props.app.name.length > 20,
    };
  }

  public componentWillUnmount(): void {
    if (!this.props.app.notFound) {
      DebugWindow.application = undefined;
      this.props.app.disableAutoRefresh();
    }
  }

  public pageApplicationOwner(): void {
    ReactInjector.pagerDutyWriter.pageApplicationOwnerModal(this.props.app);
  }

  public render() {
    const { ApplicationNav, ApplicationNavSecondary } = NgReact;

    const NotFound = this.props.app.notFound ? (
      <div>
        <h2 className="text-center">Application Not Found</h2>
        <p className="text-center" style={{ marginBottom: '20px' }}>Please check your URL - we can't find any data for <em>{this.props.app.name}</em>.</p>
      </div>
    ) : null;

    const Found = !this.props.app.notFound ? (
      <h2>
        <ApplicationIcon app={this.props.app} />
        <span className="application-name">{this.props.app.name}</span>
        <ApplicationRefresher app={this.props.app}/>
      </h2>
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
                <PagerDutyButton app={this.props.app}/>
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
