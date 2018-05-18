import { noop, IPromise } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { $timeout } from 'ngimport';

import { Application } from 'core/application/application.model';
import { ITask } from 'core/domain';
import { TaskReader } from '../task.read.service';

export interface ITaskMonitorConfig {
  title: string;
  application?: Application;
  modalInstance?: IModalServiceInstance;
  onTaskComplete?: () => any;
  onTaskRetry?: () => void;
  monitorInterval?: number;
  submitMethod?: () => IPromise<ITask>;
}

export class TaskMonitor {
  public submitting: boolean;
  public task: ITask;
  public error: boolean;
  public errorMessage: string;
  public title: string;
  public application: Application;
  public submitMethod: (params?: any) => IPromise<ITask>;
  public modalInstance: IModalServiceInstance;
  private monitorInterval: number;
  private onTaskComplete: () => any;
  private onTaskRetry: () => void;

  constructor(public config: ITaskMonitorConfig) {
    this.title = config.title;
    this.application = config.application;
    this.modalInstance = config.modalInstance;
    this.onTaskComplete = config.onTaskComplete;
    this.onTaskRetry = config.onTaskRetry;
    this.monitorInterval = config.monitorInterval || 1000;
    this.submitMethod = config.submitMethod;

    this.modalInstance.result.then(() => this.onModalClose(), () => this.onModalClose());
  }

  public onModalClose(): void {
    if (this.task && this.task.poller) {
      $timeout.cancel(this.task.poller);
    }
  }

  public closeModal(): void {
    try {
      this.modalInstance.dismiss();
    } catch (ignored) {
      // modal was already closed
    }
  }

  public startSubmit(): void {
    this.submitting = true;
    this.task = null;
    this.error = false;
    this.errorMessage = null;
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
  }

  public handleTaskSuccess(task: ITask): void {
    this.task = task;
    if (this.application && this.application.getDataSource('runningTasks')) {
      this.application.getDataSource('runningTasks').refresh();
    }
    TaskReader.waitUntilTaskCompletes(task, this.monitorInterval)
      .then(() => (this.onTaskComplete ? this.onTaskComplete() : noop))
      .catch(() => this.setError(task));
  }

  public tryToFix() {
    this.error = null;
    if (this.onTaskRetry) {
      this.onTaskRetry();
    }
  }

  public submit = (submitMethod?: () => IPromise<ITask>) => {
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
