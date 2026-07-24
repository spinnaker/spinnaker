import type { IModalServiceInstance } from 'angular-ui-bootstrap';
import { Subject } from 'rxjs';

import type { Application } from '../../application/application.model';
import type { ITask } from '../../domain';
import { TaskReader } from '../task.read.service';
import type { Deferred } from '../../utils/deferred';
import { createDeferred } from '../../utils/deferred';

export interface ITaskMonitorConfig {
  title: string;
  application?: Application;
  modalInstance?: IModalServiceInstance;
  onTaskComplete?: () => any;
  onTaskRetry?: () => void;
  monitorInterval?: number;
  submitMethod?: () => PromiseLike<ITask>;
}

export interface IModalServiceInstanceEmulation<T = any> extends IModalServiceInstance {
  deferred: Deferred<T>;
}

export class TaskMonitor {
  public submitting: boolean;
  public task: ITask;
  public error: boolean;
  public errorMessage: string;
  public title: string;
  public application: Application;
  public submitMethod: (params?: any) => PromiseLike<ITask>;
  public modalInstance: IModalServiceInstance;
  private closed = false;
  private monitorInterval: number;
  private onTaskComplete: () => any;
  private submissionGeneration = 0;
  public onTaskRetry: () => void;
  public statusUpdatedStream: Subject<void> = new Subject<void>();

  /** Use this factory in React Modal classes to emulate an AngularJS UI-Bootstrap modalInstance */
  public static modalInstanceEmulation<T = any>(
    onClose: (result: T) => void,
    onDismiss?: (result: T) => void,
  ): IModalServiceInstanceEmulation<T> {
    const deferred = createDeferred<T>();
    // handle when modal was closed
    deferred.promise.catch(() => {});
    return ({
      deferred,
      result: deferred.promise,
      close: (result: T) => {
        deferred.resolve(result);
        return onClose(result);
      },
      dismiss: (result: T) => {
        deferred.reject(result);
        return (onDismiss || onClose)(result);
      },
    } as unknown) as IModalServiceInstanceEmulation<T>;
  }

  constructor(public config: ITaskMonitorConfig) {
    this.title = config.title;
    this.application = config.application;
    this.modalInstance = config.modalInstance;
    this.onTaskComplete = config.onTaskComplete;
    this.onTaskRetry = config.onTaskRetry;
    this.monitorInterval = config.monitorInterval || 1000;
    this.submitMethod = config.submitMethod;

    if (this.modalInstance) {
      this.modalInstance.result.then(
        () => this.onModalClose(),
        () => this.onModalClose(),
      );
    }
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
    try {
      evt && evt.stopPropagation();
      this.modalInstance.dismiss();
    } catch (ignored) {
      // modal was already closed
    }
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
    (submitMethod || this.submitMethod)()
      .then((task: ITask) => this.handleTaskSuccess(task, generation))
      .catch((task: ITask) => this.setError(task, generation));
  };

  public callPreconfiguredSubmit(params: any) {
    const generation = this.startSubmit();
    if (!this.isActiveGeneration(generation)) {
      return;
    }
    this.submitMethod(params)
      .then((task: ITask) => this.handleTaskSuccess(task, generation))
      .catch((task: ITask) => this.setError(task, generation));
  }

  private isActiveGeneration(generation: number): boolean {
    return !this.closed && generation === this.submissionGeneration;
  }
}
