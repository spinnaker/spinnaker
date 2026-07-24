export type RoutingStateListener = (routing: boolean) => void;

export class RoutingState {
  private activeTransitions = new Set<symbol>();
  private listeners = new Set<RoutingStateListener>();
  private disposed = false;

  public get routing(): boolean {
    return this.activeTransitions.size > 0;
  }

  public subscribe(listener: RoutingStateListener): () => void {
    listener(this.routing);
    if (this.disposed) {
      return () => undefined;
    }
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  public begin(): () => void {
    if (this.disposed) {
      return () => undefined;
    }
    const token = Symbol('routing transition');
    const wasRouting = this.routing;
    this.activeTransitions.add(token);
    if (!wasRouting) {
      this.publish();
    }
    let finished = false;
    return () => {
      if (finished || this.disposed) return;
      finished = true;
      this.activeTransitions.delete(token);
      if (!this.routing) this.publish();
    };
  }

  public dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    const wasRouting = this.routing;
    this.activeTransitions.clear();
    if (wasRouting) this.publish();
    this.listeners.clear();
  }

  private publish(): void {
    this.listeners.forEach((listener) => listener(this.routing));
  }
}
