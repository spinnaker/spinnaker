import { IOrchestratedItem } from '../../../domain';

const runningStatuses = ['RUNNING', 'SUSPENDED'];

export class OrchestratedItemRunningTime {
  private updateInterval: any;

  constructor(private item: IOrchestratedItem, private updateCallback: (time: number) => void) {
    this.checkStatus();
  }

  public checkStatus(item = this.item): void {
    if (item && item !== this.item) {
      this.item = item;
    }

    if (!runningStatuses.includes(this.item.status)) {
      this.reset();
      this.updateCallback(this.item.runningTimeInMs);
    }
    if (runningStatuses.includes(this.item.status) && !this.updateInterval) {
      this.updateInterval = setInterval(() => this.updateCallback(Date.now() - this.item.startTime), 1000);
      this.updateCallback(Date.now() - this.item.startTime);
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
