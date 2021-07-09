import { ApolloProvider } from '@apollo/client';
import { UIView } from '@uirouter/react';
import React from 'react';

import { ApplicationContextProvider } from './ApplicationContext';
import { Application } from './application.model';
import { RecentHistoryService } from '../history';
import { createApolloClient } from '../managed/graphql/client';
import { ApplicationNavigation } from './nav/ApplicationNavigation';
import { DebugWindow } from '../utils/consoleDebug';

import './application.less';

export interface IApplicationComponentProps {
  app: Application;
}

export class ApplicationComponent extends React.Component<IApplicationComponentProps> {
  private apolloClient = createApolloClient();
  private unsubscribeAppRefresh?: () => void;

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
    // KLUDGE: warning, do not use, this is temporarily and will be removed very soon.
    if (!app.attributes?.disableAutoRefresh) {
      this.unsubscribeAppRefresh = this.props.app.subscribeToRefresh(this.apolloClient.onRefresh);
      app.enableAutoRefresh();
    }
  }

  private unmountApplication(app: Application) {
    if (app.notFound || app.hasError) {
      return;
    }
    DebugWindow.application = undefined;
    this.unsubscribeAppRefresh?.();
    this.unsubscribeAppRefresh = undefined;
    app.disableAutoRefresh();
  }

  public render() {
    const { app } = this.props;

    return (
      <div className="application">
        {!app.notFound && !app.hasError && <ApplicationNavigation app={app} />}
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
        <ApplicationContextProvider app={app}>
          <ApolloProvider client={this.apolloClient.client}>
            <div className="container scrollable-columns">
              <UIView className="secondary-panel" name="insight" />
            </div>
          </ApolloProvider>
        </ApplicationContextProvider>
      </div>
    );
  }
}
