export abstract class BasePluginManager<T extends { kind: string }> {
  private handlers: { [kind in string]?: T } = {}; // The ? forces us to do proper null check

  constructor(baseHandlers: T[]) {
    for (const handler of baseHandlers) {
      this.registerHandler(handler);
    }
  }

  protected normalizeKind(kind: string): string {
    // Removes the version of the handler
    return kind.split('@')[0];
  }

  public getHandler(kind: string): T | undefined {
    // We first try to return an exact match, otherwise, we return the handler without the version
    return this.handlers[kind] || this.handlers[this.normalizeKind(kind)];
  }

  public isSupported(kind: string) {
    return Boolean(this.getHandler(kind));
  }

  public registerHandler(handler: T) {
    // We register both the handler with the version and without the version
    this.handlers[handler.kind] = handler;
    this.handlers[this.normalizeKind(handler.kind)] = handler;
  }
}
