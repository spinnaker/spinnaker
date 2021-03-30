import { Task, WorkerPool } from './workerPool';

const delay = (millis = 0) => new Promise<void>((resolve) => setTimeout(resolve, millis));

// Testing class to track running tasks
class TaskTracker {
  public runningTasks: number[] = [];

  private start(id: number) {
    this.runningTasks.push(id);
  }

  private stop(id: number) {
    const { runningTasks } = this;
    const idx = runningTasks.indexOf(id);
    if (idx === -1) {
      throw new Error(`Task ${id} not found in runningTasks ${JSON.stringify(runningTasks)}`);
    }
    runningTasks.splice(idx, 1);
  }

  public createTask(id: number, task: Task) {
    return () => {
      this.start(id);
      const promise = task();
      promise.then(
        () => this.stop(id),
        () => this.stop(id),
      );
      return promise;
    };
  }
}

describe('Worker pool', () => {
  describe('.task()', () => {
    it('registers a task and returns a promise that resolves to the task result', (done) => {
      new WorkerPool(1)
        .task(() => Promise.resolve(2))
        .then((val) => expect(val).toBe(2))
        .then(done);
    });

    it('runs one task at a time when concurrency === 1', async () => {
      const pool = new WorkerPool(1);
      const tracker = new TaskTracker();

      const promise1 = pool.task(tracker.createTask(1, () => delay().then(() => 'one')));
      const promise2 = pool.task(tracker.createTask(2, () => delay().then(() => 'two')));
      expect(tracker.runningTasks).toEqual([1]);

      const result1 = await promise1;
      expect(result1).toEqual('one');
      expect(tracker.runningTasks).toEqual([2]);

      const result2 = await promise2;
      expect(result2).toEqual('two');
      expect(tracker.runningTasks).toEqual([]);
    });

    it('runs two tasks at a time when concurrency === 2', async () => {
      const pool = new WorkerPool(2);
      const tracker = new TaskTracker();

      const promise1 = pool.task(tracker.createTask(1, () => delay().then(() => 'one')));
      const promise2 = pool.task(tracker.createTask(2, () => delay().then(() => 'two')));
      const promise3 = pool.task(tracker.createTask(3, () => delay().then(() => 'three')));
      const promise4 = pool.task(tracker.createTask(4, () => delay().then(() => 'four')));

      expect(tracker.runningTasks).toEqual([1, 2]);

      const result1 = await promise1;
      expect(result1).toEqual('one');
      expect(tracker.runningTasks).toEqual([2, 3]);

      const result2 = await promise2;
      expect(result2).toEqual('two');
      expect(tracker.runningTasks).toEqual([3, 4]);

      const result3 = await promise3;
      expect(result3).toEqual('three');
      expect(tracker.runningTasks).toEqual([4]);

      const result4 = await promise4;
      expect(result4).toEqual('four');
      expect(tracker.runningTasks).toEqual([]);
    });
  });

  describe('.cancelAll()', () => {
    it('cancels running tasks and rejects their promises', async () => {
      const pool = new WorkerPool(1);
      const tracker = new TaskTracker();

      const promise1 = pool.task(tracker.createTask(1, () => delay().then(() => 'one')));
      const promise2 = pool.task(tracker.createTask(2, () => delay().then(() => 'two')));

      const result1 = await promise1;
      expect(result1).toEqual('one');

      pool.cancelAll('cancel reason');
      try {
        const result2 = await promise2;
        fail(`promise2 should have been rejected but was ${result2}`);
      } catch (error) {
        expect(error).toBe('cancel reason');
      }
    });

    it('cancels running AND pending tasks and rejects their promises', async () => {
      const pool = new WorkerPool(1);
      const tracker = new TaskTracker();

      const promise1 = pool.task(tracker.createTask(1, () => delay().then(() => 'one')));
      const promise2 = pool.task(tracker.createTask(2, () => delay().then(() => 'two')));
      const promise3 = pool.task(tracker.createTask(2, () => delay().then(() => 'three')));

      pool.cancelAll('cancel reason');

      try {
        const result1 = await promise1;
        fail(`promise1 should have been rejected but was ${result1}`);
      } catch (error) {
        expect(error).toBe('cancel reason');
      }

      try {
        const result2 = await promise2;
        fail(`promise2 should have been rejected but was ${result2}`);
      } catch (error) {
        expect(error).toBe('cancel reason');
      }

      try {
        const result3 = await promise3;
        fail(`promise3 should have been rejected but was ${result3}`);
      } catch (error) {
        expect(error).toBe('cancel reason');
      }
    });
  });
});
