import { ApplicationHeader } from './nav/ApplicationHeader';
import React from 'react';
import { UIView } from '@uirouter/react';

import { Application } from 'core/application';
import { RecentHistoryService } from 'core/history';
import { DebugWindow } from 'core/utils/consoleDebug';

import './application.less';

export interface IApplicationComponentProps {
  app: Application;
}

export class ApplicationComponent extends React.Component<IApplicationComponentProps> {
  constructor(props: IApplicationComponentProps) {
    super(props);
    this.mountApplication(props.app);
  }

  public componentWillUnmount(): void {
    this.unmountApplication(this.props.app);
  }

  public componentWillReceiveProps(nextProps: IApplicationComponentProps): void {
    this.unmountApplication(this.props.app);
    this.mountApplication(nextProps.app);
  }

  private mountApplication(app: Application) {
    if (app.notFound || app.hasError) {
      RecentHistoryService.removeLastItem('applications');
      return;
    }

    DebugWindow.application = app;
    app.enableAutoRefresh();
  }

  private unmountApplication(app: Application) {
    if (app.notFound || app.hasError) {
      return;
    }
    DebugWindow.application = undefined;
    app.disableAutoRefresh();
  }

  public render() {
    const { app } = this.props;
    return (
      <div className="application">
        {!app.notFound && !app.hasError && <ApplicationHeader app={app} />}
        {app.notFound && (
          <div>
            <h2 className="text-center">Application Not Found</h2>
            <p className="text-center" style={{ marginBottom: '20px' }}>
              Please check your URL - we can't find any data for <em>{app.name}</em>.
            </p>
          </div>
        )}
        {app.hasError && (
          <div>
            <h2 className="text-center">Something went wrong</h2>
            <p className="text-center" style={{ marginBottom: '20px' }}>
              There was a problem loading <em>{app.name}</em>. Try checking your browser console for errors.
            </p>
          </div>
        )}
        <div className="container scrollable-columns">
          <UIView className="secondary-panel" name="insight" />
        </div>
      </div>
    );
  }
}
