import { StateObject, UIRouter } from '@uirouter/core';
import { ReactViewDeclaration } from '@uirouter/react';
import { module } from 'angular';
import React, { ErrorInfo } from 'react';

import { CollapsibleSection } from './collapsibleSection/CollapsibleSection';
import { ValidationMessage } from './forms/validation/ValidationMessage';
import { logger } from '../utils/Logger';

interface ISpinErrorBoundaryState {
  error?: Error;
  retrying: boolean;
}

interface ISpinErrorBoundaryProps {
  children: React.ReactNode;
  category: string;
}

/** Given a React Component, returns a React Component wrapped in an ErrorBoundary */
export function withErrorBoundary<T>(Component: React.ComponentType<T>, category: string) {
  return React.forwardRef((props: T, ref) => (
    <SpinErrorBoundary category={category}>
      <Component {...props} ref={ref} />
    </SpinErrorBoundary>
  )) as React.ComponentType<T>;
}

export class SpinErrorBoundary extends React.Component<ISpinErrorBoundaryProps, ISpinErrorBoundaryState> {
  constructor(props: ISpinErrorBoundaryProps) {
    super(props);
    this.state = { error: null, retrying: false };
  }

  static getDerivedStateFromError(error: Error) {
    // Update state so the next render will show the fallback UI.
    return { error };
  }

  componentDidCatch(error: Error, _errorInfo: ErrorInfo) {
    logger.log({
      level: 'ERROR',
      category: `SpinErrorBoundary - ${this.props.category}`,
      action: error.message,
      error,
      data: { label: error.message, componentStack: _errorInfo?.componentStack },
    });
  }

  retry() {
    this.setState({ retrying: true });
    setTimeout(() => this.setState({ retrying: false, error: null }), 250);
  }

  render() {
    if (!this.state?.error) {
      return this.props.children;
    }

    const { message, stack } = this.state.error;
    const { retrying } = this.state;

    return (
      <div className="flex-container-v sp-margin-l">
        <h3>Oh dear, something has gone wrong.</h3>
        <div className="sp-margin-l-bottom">
          <h4>Spinnaker has encountered an unexpected UI error.</h4>
          <button disabled={retrying} onClick={() => this.retry()}>
            {retrying ? 'Retrying...' : 'Try again'}
          </button>
        </div>

        {message && <ValidationMessage message={message} type="error" />}

        {stack && (
          <CollapsibleSection defaultExpanded={false} heading="Stacktrace">
            <pre style={{ overflow: 'scroll' }}>{stack}</pre>
          </CollapsibleSection>
        )}
      </div>
    );
  }
}

export const UI_ROUTER_REACT_ERROR_BOUNDARY = 'ui.router.react.error.boundary';
module(UI_ROUTER_REACT_ERROR_BOUNDARY, ['ui.router']).config([
  '$uiRouterProvider',
  ($uiRouterProvider: UIRouter) => {
    $uiRouterProvider.stateRegistry.decorator('views', (state, parent) => {
      const views: StateObject['views'] = parent(state);

      Object.values(views)
        .filter((view) => view.$type === 'react')
        .forEach((view) => {
          const reactView = view as ReactViewDeclaration;
          const RoutedComponent = reactView.component;
          reactView.component = (props: any) => (
            <SpinErrorBoundary category={state.name}>
              <RoutedComponent {...props} />
            </SpinErrorBoundary>
          );
        });

      return views;
    });
  },
]);
