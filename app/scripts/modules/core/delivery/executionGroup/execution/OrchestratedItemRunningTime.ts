import { IOrchestratedItem } from 'core/domain/IOrchestratedItem';

export class OrchestratedItemRunningTime {
  private updateInterval: any;

  constructor(private item: IOrchestratedItem, private updateCallback: (time: number) => void) {
    this.checkStatus();
  }

  public checkStatus(): void {
    if (this.item.status !== 'RUNNING' && this.updateInterval) {
      this.reset();
      this.updateCallback(this.item.runningTimeInMs);
    }
    if (this.item.status === 'RUNNING' && !this.updateInterval) {
      this.reset();
      this.updateInterval = setInterval(() => this.updateCallback(Date.now() - this.item.startTime), 1000);
    }
    return undefined;
  }

  public reset() {
    if (this.updateInterval) {
      clearInterval(this.updateInterval);
      this.updateInterval = undefined;
    }
  }
}
