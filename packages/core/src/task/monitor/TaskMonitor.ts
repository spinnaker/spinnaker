import { IDeferred } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { $q, $timeout } from 'ngimport';
import { Subject } from 'rxjs';

import { Application } from '../../application/application.model';
import { ITask } from '../../domain';

import { TaskReader } from '../task.read.service';

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
  deferred: IDeferred<T>;
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
  private monitorInterval: number;
  private onTaskComplete: () => any;
  public onTaskRetry: () => void;
  public statusUpdatedStream: Subject<void> = new Subject<void>();

  /** Use this factory in React Modal classes to emulate an AngularJS UI-Bootstrap modalInstance */
  public static modalInstanceEmulation<T = any>(
    onClose: (result: T) => void,
    onDismiss?: (result: T) => void,
  ): IModalServiceInstanceEmulation {
    const deferred = $q.defer();
    // handle when modal was closed
    deferred.promise.catch(() => {});
    return {
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
    } as IModalServiceInstanceEmulation;
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
    if (this.task && this.task.poller) {
      $timeout.cancel(this.task.poller);
    }
  }

  public closeModal = (evt?: React.MouseEvent<any>): void => {
    try {
      evt && evt.stopPropagation();
      this.modalInstance.dismiss();
    } catch (ignored) {
      // modal was already closed
    }
  };

  public startSubmit(): void {
    this.submitting = true;
    this.task = null;
    this.error = false;
    this.errorMessage = null;
    document.activeElement && (document.activeElement as HTMLElement).blur();
    this.statusUpdatedStream.next();
  }

  public setError(task?: ITask): void {
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

  private handleTaskComplete(): void {
    this.onTaskComplete?.();
    this.statusUpdatedStream.next();
  }

  public handleTaskSuccess(task: ITask): void {
    this.task = task;
    if (this.application && this.application.getDataSource('runningTasks')) {
      this.application.getDataSource('runningTasks').refresh();
    }
    TaskReader.waitUntilTaskCompletes(task, this.monitorInterval, this.statusUpdatedStream)
      .then(() => this.handleTaskComplete())
      .catch(() => this.setError(task));
    this.statusUpdatedStream.next();
  }

  public tryToFix = () => {
    this.error = null;
    if (this.onTaskRetry) {
      this.onTaskRetry();
    }
    this.statusUpdatedStream.next();
  };

  public submit = (submitMethod?: () => PromiseLike<ITask>) => {
    this.startSubmit();
    (submitMethod || this.submitMethod)()
      .then((task: ITask) => this.handleTaskSuccess(task))
      .catch((task: ITask) => this.setError(task));
  };

  public callPreconfiguredSubmit(params: any) {
    this.startSubmit();
    this.submitMethod(params)
      .then((task: ITask) => this.handleTaskSuccess(task))
      .catch((task: ITask) => this.setError(task));
  }
}
