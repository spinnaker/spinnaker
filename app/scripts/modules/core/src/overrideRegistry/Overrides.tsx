import * as React from 'react';

import { CloudProviderRegistry } from 'core/cloudProvider';
import { OverrideRegistry } from 'core/overrideRegistry';

/**
 * Registers a component as a replacement for some other component.
 *
 * This is a Class Decorator which should be applied to a React Component Class.
 * The component class will be used instead of the OverridableComponent with the same key.
 *
 * If an (optional) cloudProvider is specified, the component override takes effect only for that cloud provider.
 * If an (optional) cloudProviderVersion is specified, the component override takes effect only for a specific version of that cloud provider.
 *
 * @Overrides('overrideKey', "aws", "v2")
 * class MyOverridingCmp extends React.Component {
 *   render() { return <h1>Overridden component</h1> }
 * }
 */
export function Overrides(key: string, cloudProvider?: string, cloudProviderVersion?: string) {
  return function<P, T extends React.ComponentClass<P>>(targetComponent: T): void {
    overrideRegistrationQueue.register(targetComponent, key, cloudProvider, cloudProviderVersion);
  };
}

/**
 * This queues OverrideComponent registration until the registries are ready.
 */
export class RegistrationQueue {
  private queue: Function[] = [];

  private overrideRegistry: OverrideRegistry;

  public setRegistries(overrideRegistry: OverrideRegistry) {
    this.overrideRegistry = overrideRegistry;

    this.flush();
  }

  public register(component: React.ComponentClass, key: string, cloudProvider?: string, cloudProviderVersion?: string) {
    this.queue.push(() => {
      if (!cloudProvider) {
        this.overrideRegistry.overrideComponent(key, component);
      } else {
        CloudProviderRegistry.overrideValue(cloudProvider, key, component, cloudProviderVersion);
      }
    });

    this.flush();
  }

  private flush() {
    if (!this.overrideRegistry || !CloudProviderRegistry) {
      return;
    }

    while (this.queue.length) {
      this.queue.splice(0, 1)[0]();
    }
  }
}

export const overrideRegistrationQueue = new RegistrationQueue();
