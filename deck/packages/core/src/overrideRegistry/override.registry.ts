import type React from 'react';

import { overrideRegistrationQueue } from './Overrides';

export class OverrideRegistry {
  private componentOverrides: Map<string, React.ComponentType> = new Map();

  public overrideComponent(key: string, val: React.ComponentType) {
    this.componentOverrides.set(key, val);
  }

  public getComponent<T>(key: string) {
    return this.componentOverrides.get(key) as React.ComponentClass<T>;
  }
}

export const overrideRegistry = new OverrideRegistry();

overrideRegistrationQueue.setRegistries(overrideRegistry);
