import {module} from 'angular';

export class HelpContentsRegistry {
  private helpFields: Map<string, string> = new Map<string, string>();
  private overrides: Set<string> = new Set<string>();

  /**
   * Returns the configured help value, or null if nothing is configured
   * @param key the key
   * @returns the configured help value, or null
   */
  public getHelpField(key: string): string {
    return this.helpFields.get(key) || null;
  }

  /**
   * Adds a help field to the registry. This value can be overridden by calling #registerOverride
   * If an override has been configured, this call will *not* change the already-configured value
   * @param key the key
   * @param val the value
   */
  public register(key: string, val: string): void {
    if (!this.overrides.has(key)) {
      this.helpFields.set(key, val);
    }
  }

  /**
   * Adds a help field to the registry and locks it, preventing subsequent registrations with the same key from
   * replacing its value. Subsequent calls to this method with the same key will have no effect on the value.
   *
   * @param key the key
   * @param val the value
   */
  public registerOverride(key: string, val: string): void {
    this.register(key, val);
    this.overrides.add(key);
  }
}

export const HELP_CONTENTS_REGISTRY = 'spinnaker.core.help.registry';
module(HELP_CONTENTS_REGISTRY, [])
  .service('helpContentsRegistry', HelpContentsRegistry);
