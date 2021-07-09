import React from 'react';

import { CloudProviderRegistry } from '../cloudProvider';

import { OverrideRegistry } from './override.registry';

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

  public register(component: React.ComponentType, key: string, cloudProvider?: string) {
    this.queue.push(() => {
      if (!cloudProvider) {
        this.overrideRegistry.overrideComponent(key, component);
      } else {
        CloudProviderRegistry.overrideValue(cloudProvider, key, component);
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

/**
 * Registers a component as a replacement for some other component.
 *
 * This is a Class Decorator which should be applied to a React Component Class.
 * The component class will be used instead of the OverridableComponent with the same key.
 *
 * If an (optional) cloudProvider is specified, the component override takes effect only for that cloud provider.
 *
 * @Overrides('overrideKey', "aws")
 * class MyOverridingCmp extends React.Component {
 *   render() { return <h1>Overridden component</h1> }
 * }
 */
export function Overrides(key: string, cloudProvider?: string) {
  return function <P, T extends React.ComponentClass<P>>(targetComponent: T): void {
    overrideRegistrationQueue.register(targetComponent, key, cloudProvider);
  };
}

/**
 * Registers a component as a replacement for some other component.
 *
 * This should be applied to a React Function Component, since they cannot utilize decorators.
 * The component class will be used instead of the OverridableComponent with the same key.
 *
 * If an (optional) cloudProvider is specified, the component override takes effect only for that cloud provider.
 * If not, this is the same as using the override registry to override the component.
 */
export const overridesComponent = (targetComponent: React.ComponentType, key: string, cloudProvider?: string) => {
  overrideRegistrationQueue.register(targetComponent, key, cloudProvider);
};
