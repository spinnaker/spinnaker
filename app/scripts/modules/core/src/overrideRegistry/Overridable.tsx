import { get } from 'lodash';
import React from 'react';
import { of as observableOf, Subject } from 'rxjs';
import { map, switchMap, takeUntil } from 'rxjs/operators';

import { AccountService, IAccountDetails } from '../account/AccountService';
import { CloudProviderRegistry } from '../cloudProvider';
import { AngularJSAdapter, ReactInjector } from '../reactShims';
import { Spinner } from '../widgets';

export interface IOverridableProps {
  accountId?: string;
  forwardedRef?: React.Ref<any>;
  OriginalComponent?: React.ComponentClass;
}

/**
 * Enables this component to be overriden by some other component.
 *
 * This is a Class Decorator which should be applied to a React Component Class.
 *
 * When rendered, the component will first check if an overriding component is registered using the same key.
 * If yes, the overriding component is rendered.
 * If no, the decorated component itself is rendered.
 *
 * @Overridable('overrideKey')
 * class MyCmp extends React.Component {
 *   render() { return <h1>Overridable Component</h1> }
 * }
 *
 * When using the component, just render it as usual:
 * <MyCmp/>
 *
 * If the override is cloud provider specific, pass the accountId as a prop:
 * <MyCmp accountId={accountId} />
 */
export function Overridable(key: string) {
  return function <P, T extends React.ComponentClass<P>>(targetComponent: T): T {
    return overridableComponent(targetComponent, key);
  };
}

/**
 * A high order component which returns a delegating component.
 * The component will delegate to the overriding component registered with OverrideRegistry or CloudProviderRegistry.
 * If no override is registered, it delegates to the component being decorated.
 *
 * class MyCmp extends React.Component {
 *   render() { return <h1>Overridable Component</h1>
 * }
 *
 * export const MyOverridableCmp = overridableComponent(MyCmp);
 */
export function overridableComponent<P extends IOverridableProps, T extends React.ComponentType<P>>(
  OriginalComponent: T,
  key: string,
): T {
  class OverridableComponent extends React.Component<P, { Component: T }> {
    public static OriginalComponent: T = OriginalComponent;

    private account$ = new Subject<string>();
    private destroy$ = new Subject();

    constructor(props: P) {
      super(props);

      let constructing = true;

      this.account$
        .pipe(
          switchMap((accountName) => {
            if (!accountName) {
              return observableOf(null);
            }

            return AccountService.accounts$.pipe(map((accts) => accts.find((acct) => acct.name === accountName)));
          }),
          map((accountDetails: IAccountDetails) => this.getComponent(accountDetails)),
          takeUntil(this.destroy$),
        )
        .subscribe((Component) => {
          // The component may be ready synchronously (when the constructor is run), or it might require async.
          // Handle either case here
          if (constructing) {
            this.state = { Component };
          } else {
            this.setState({ Component });
          }
        });

      this.account$.next(this.props.accountId);
      constructing = false;
    }

    public componentWillUnmount() {
      this.destroy$.next();
    }

    public componentWillReceiveProps(nextProps: P) {
      const { accountId } = nextProps;
      if (this.props.accountId !== accountId) {
        this.account$.next(accountId);
      }
    }

    private getComponentFromCloudProvider(accountDetails: IAccountDetails): T {
      const { cloudProvider } = accountDetails;
      if (!cloudProvider) {
        return null;
      }

      const CloudProviderComponentOverride = CloudProviderRegistry.getValue(cloudProvider, key);
      if (CloudProviderComponentOverride) {
        return CloudProviderComponentOverride as T;
      }

      const cloudProviderTemplateOverride = CloudProviderRegistry.getValue(cloudProvider, key + 'TemplateUrl');
      if (cloudProviderTemplateOverride) {
        const cloudProviderController = CloudProviderRegistry.getValue(cloudProvider, key + 'Controller');
        const controllerAs = cloudProviderController && cloudProviderController.includes(' as ') ? undefined : 'ctrl';
        const Component = (props: any) => (
          <AngularJSAdapter
            {...props}
            templateUrl={cloudProviderTemplateOverride}
            controller={cloudProviderController}
            controllerAs={controllerAs}
          />
        );

        return (Component as any) as T;
      }

      return null;
    }

    private getComponentFromOverrideRegistry(): T {
      const { overrideRegistry } = ReactInjector;

      const ComponentOverride = overrideRegistry.getComponent(key);
      if (ComponentOverride) {
        return ComponentOverride as T;
      }

      const templateOverride: string = overrideRegistry.getTemplate(key, null);
      if (templateOverride) {
        const controllerOverride: string = overrideRegistry.getController(key, null);
        const controllerAs = controllerOverride && controllerOverride.includes(' as ') ? undefined : 'ctrl';
        const Component = (props: any) => (
          <AngularJSAdapter
            {...props}
            templateUrl={templateOverride}
            controller={controllerOverride}
            controllerAs={controllerAs}
          />
        );

        return (Component as any) as T;
      }

      return null;
    }

    private getComponent(accountDetails: IAccountDetails): T {
      return (
        this.getComponentFromCloudProvider(accountDetails || ({} as any)) ||
        this.getComponentFromOverrideRegistry() ||
        OriginalComponent
      );
    }

    public render() {
      const Component = this.state && this.state.Component;
      const isOverridden = Component && Component !== OriginalComponent;
      const props = { ...(this.props as any), ...(isOverridden ? { OriginalComponent } : {}) };

      if (!Component) {
        return <Spinner />;
      }

      const isClassComponent = ['render', 'prototype.render'].some(
        (prop) => typeof get(Component, prop) === 'function',
      );
      return isClassComponent ? <Component {...props} ref={this.props.forwardedRef} /> : <Component {...props} />;
    }
  }

  const forwardRef = (React.forwardRef<T, P>((props, ref) => (
    <OverridableComponent {...props} forwardedRef={ref} />
  )) as unknown) as T;

  // Copy static properties
  Object.getOwnPropertyNames(OriginalComponent)
    .filter((propName) => propName !== 'constructor' && !OverridableComponent.hasOwnProperty(propName))
    .forEach((propName) => ((forwardRef as any)[propName] = (OriginalComponent as any)[propName]));

  return forwardRef;
}
