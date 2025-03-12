import { isUndefined, without } from 'lodash';

export type Task<T = any> = () => Promise<T>;

class Worker<T> {
  public workerPromise: Promise<T>;
  private resolve: (resolvedValue: T) => void;
  private reject: (error: any) => void;

  constructor(private task: Task<T>) {
    this.workerPromise = new Promise<T>((resolve, reject) => Object.assign(this, { resolve, reject }));
  }

  public run(): Promise<T> {
    const taskPromise = this.task();
    taskPromise.then(this.resolve, this.reject);
    return taskPromise;
  }

  public cancel(reason?: any) {
    this.reject(isUndefined(reason) ? 'Cancelled' : reason);
  }
}

export class WorkerPool<T = any> {
  private queuedTasks: Array<Worker<T>> = [];
  private runningTasks: Array<Worker<T>> = [];
  constructor(private concurrency: number) {}

  public task(task: Task<T>): Promise<T> {
    const worker = new Worker(task);

    this.queuedTasks.push(worker);
    this.flush();

    return worker.workerPromise;
  }

  public cancelAll(reason?: any) {
    []
      .concat(this.runningTasks)
      .concat(this.queuedTasks)
      .forEach((task) => task.cancel(reason));
    this.runningTasks = [];
    this.queuedTasks = [];
  }

  private flush() {
    const { queuedTasks, runningTasks } = this;

    if (queuedTasks.length && runningTasks.length < this.concurrency) {
      this.runTask(queuedTasks.shift());
    }
  }

  private completeTask(worker: Worker<T>) {
    this.runningTasks = without(this.runningTasks, worker);
    this.flush();
  }

  private runTask(worker: Worker<T>) {
    this.runningTasks.push(worker);
    const done = () => this.completeTask(worker);
    worker.run().then(done, done);
  }
}
