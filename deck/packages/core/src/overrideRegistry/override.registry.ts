import { module } from 'angular';
import type React from 'react';

import { overrideRegistrationQueue } from './Overrides';

export class OverrideRegistry {
  private componentOverrides: Map<string, React.ComponentType> = new Map();

  public overrideTemplate(key: string, _val: string) {
    this.warnDeprecated(
      `OverrideRegistry.overrideTemplate("${key}") is deprecated. Angular template overrides are no longer rendered; migrate to overrideComponent(key, Component).`,
    );
  }

  public overrideComponent(key: string, val: React.ComponentType) {
    this.componentOverrides.set(key, val);
  }

  public overrideController(key: string, _val: string) {
    this.warnDeprecated(
      `OverrideRegistry.overrideController("${key}") is deprecated. Angular controller overrides are no longer rendered; migrate to overrideComponent(key, Component).`,
    );
  }

  public getTemplate(key: string, _defaultVal?: string): null {
    this.warnDeprecated(
      `OverrideRegistry.getTemplate("${key}") is deprecated. Angular template overrides are no longer rendered; migrate to getComponent(key).`,
    );
    return null;
  }

  public getComponent<T>(key: string) {
    return this.componentOverrides.get(key) as React.ComponentClass<T>;
  }

  public getController(key: string, _defaultVal?: string): null {
    this.warnDeprecated(
      `OverrideRegistry.getController("${key}") is deprecated. Angular controller overrides are no longer rendered; migrate to getComponent(key).`,
    );
    return null;
  }

  private warnDeprecated(message: string) {
    console.warn(message);
  }
}

export const OVERRIDE_REGISTRY = 'spinnaker.core.override.registry';
module(OVERRIDE_REGISTRY, [])
  .service('overrideRegistry', OverrideRegistry)
  .run([
    'overrideRegistry',
    (overrideRegistry: OverrideRegistry) => {
      overrideRegistrationQueue.setRegistries(overrideRegistry);
    },
  ]);
