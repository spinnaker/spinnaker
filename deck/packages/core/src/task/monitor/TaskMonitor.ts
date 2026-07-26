import { Subject } from 'rxjs';

import type { Application } from '../../application/application.model';
import type { ITask } from '../../domain';
import { TaskReader } from '../task.read.service';

export interface ITaskMonitorConfig {
  title: string;
  application?: Application;
  onDismiss?: (reason?: unknown) => unknown;
  onTaskComplete?: () => any;
  onTaskRetry?: () => void;
  monitorInterval?: number;
  submitMethod?: () => PromiseLike<ITask>;
}

export class TaskMonitor {
  public submitting: boolean;
  public task: ITask;
  public error: boolean;
  public errorMessage: string;
  public title: string;
  public application: Application;
  public submitMethod: (params?: any) => PromiseLike<ITask>;
  private closed = false;
  private monitorInterval: number;
  private onDismiss: (reason?: unknown) => unknown;
  private onTaskComplete: () => any;
  private submissionGeneration = 0;
  public onTaskRetry: () => void;
  public statusUpdatedStream: Subject<void> = new Subject<void>();

  constructor(public config: ITaskMonitorConfig) {
    this.title = config.title;
    this.application = config.application;
    this.onDismiss = config.onDismiss;
    this.onTaskComplete = config.onTaskComplete;
    this.onTaskRetry = config.onTaskRetry;
    this.monitorInterval = config.monitorInterval || 1000;
    this.submitMethod = config.submitMethod;
  }

  public hasDismissHandler(): boolean {
    return Boolean(this.onDismiss);
  }

  public onModalClose(): void {
    if (this.closed) {
      return;
    }
    this.closed = true;
    this.submissionGeneration++;
    TaskReader.cancelPolling(this.task);
  }

  public closeModal = (evt?: React.MouseEvent<any>): void => {
    evt?.stopPropagation();
    if (this.closed) {
      return;
    }
    this.onModalClose();
    this.onDismiss?.();
  };

  public startSubmit(): number {
    const generation = ++this.submissionGeneration;
    if (!this.isActiveGeneration(generation)) {
      return generation;
    }
    TaskReader.cancelPolling(this.task);
    this.submitting = true;
    this.task = null;
    this.error = false;
    this.errorMessage = null;
    document.activeElement && (document.activeElement as HTMLElement).blur();
    this.statusUpdatedStream.next();
    return generation;
  }

  public setError(task?: ITask, generation = this.submissionGeneration): void {
    if (!this.isActiveGeneration(generation)) {
      return;
    }
    if (task) {
      this.task = task;
      this.errorMessage = task.failureMessage || 'There was an unknown server error.';
    } else {
      this.errorMessage = 'There was an unknown server error.';
    }
    this.submitting = false;
    this.error = true;
    this.statusUpdatedStream.next();
  }

  private handleTaskComplete(generation: number): void {
    if (!this.isActiveGeneration(generation)) {
      return;
    }
    this.onTaskComplete?.();
    if (!this.isActiveGeneration(generation)) {
      return;
    }
    this.statusUpdatedStream.next();
  }

  public handleTaskSuccess(task: ITask, generation = this.submissionGeneration): void {
    if (!this.isActiveGeneration(generation)) {
      return;
    }
    this.task = task;
    if (this.application && this.application.getDataSource('runningTasks')) {
      this.application.getDataSource('runningTasks').refresh();
    }
    if (!this.isActiveGeneration(generation)) {
      return;
    }
    TaskReader.waitUntilTaskCompletes(task, this.monitorInterval, this.statusUpdatedStream)
      .then(() => this.handleTaskComplete(generation))
      .catch(() => this.setError(task, generation));
    this.statusUpdatedStream.next();
  }

  public tryToFix = () => {
    if (this.closed) {
      return;
    }
    const generation = this.submissionGeneration;
    this.error = null;
    if (this.onTaskRetry) {
      this.onTaskRetry();
    }
    if (!this.isActiveGeneration(generation)) {
      return;
    }
    this.statusUpdatedStream.next();
  };

  public submit = (submitMethod?: () => PromiseLike<ITask>) => {
    const generation = this.startSubmit();
    if (!this.isActiveGeneration(generation)) {
      return;
    }
    Promise.resolve((submitMethod || this.submitMethod)())
      .then((task: ITask) => this.handleTaskSuccess(task, generation))
      .catch((task: ITask) => this.setError(task, generation));
  };

  public callPreconfiguredSubmit(params: any) {
    const generation = this.startSubmit();
    if (!this.isActiveGeneration(generation)) {
      return;
    }
    Promise.resolve(this.submitMethod(params))
      .then((task: ITask) => this.handleTaskSuccess(task, generation))
      .catch((task: ITask) => this.setError(task, generation));
  }

  private isActiveGeneration(generation: number): boolean {
    return !this.closed && generation === this.submissionGeneration;
  }
}
