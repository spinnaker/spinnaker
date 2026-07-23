import { OrchestratedItemTransformer } from './orchestratedItem.transformer';
import { getDirectRouter, setDirectRouter } from '../navigation/directRouter';

describe('orchestratedItem transformer', () => {
  let previousRouter: ReturnType<typeof getDirectRouter>;

  beforeEach(() => (previousRouter = getDirectRouter()));
  afterEach(() => setDirectRouter(previousRouter));

  describe('failure message extraction', () => {
    const getMessage = (obj: any) => {
      OrchestratedItemTransformer.defineProperties(obj);
      return obj.failureMessage;
    };

    it('returns null when no stage context', () => {
      expect(getMessage({})).toBe(null);
    });

    it('returns null when no kato.tasks field in stage context', () => {
      expect(getMessage({ context: {} })).toBe(null);
    });

    it('returns null when kato.tasks field in stage context is empty', () => {
      expect(getMessage({ context: { 'kato.tasks': [] } })).toBe(null);
    });

    it('returns null when last kato task has no exception', () => {
      const stage = {
        context: {
          'kato.tasks': [
            {
              exception: {
                message: 'failed!',
              },
            },
            {},
          ],
        },
      };
      expect(getMessage(stage)).toBe(null);
    });

    it('returns general exception if present', () => {
      expect(getMessage({ context: { exception: { details: { errors: ['E1', 'E2'] } } } })).toBe('E1\n\nE2');
      expect(getMessage({ context: { exception: { details: { errors: [] } } } })).toBe(null);
      expect(getMessage({ context: {} })).toBe(null);
    });

    it('returns combined exception if both kato and general exceptions are present', () => {
      const stage = {
        context: {
          'kato.tasks': [
            {
              status: {
                failed: true,
              },
              exception: {
                message: 'failed!',
              },
            },
          ],
          exception: {
            details: {
              errors: ['E1', 'E2'],
            },
          },
        },
      };
      expect(getMessage(stage)).toBe('E1\n\nE2\n\nfailed!');
    });

    it('returns exception when it is in the last kato task', () => {
      const stage = {
        context: {
          'kato.tasks': [
            {
              message: 'this one is fine',
            },
            {
              status: {
                failed: true,
              },
              exception: {
                message: 'failed!',
              },
            },
          ],
        },
      };
      expect(getMessage(stage)).toBe('failed!');
    });

    it('extracts exception object from variables', () => {
      const task = {
        variables: [
          {
            key: 'exception',
            value: {
              details: {
                error: 'From exception object',
              },
            },
          },
        ],
      };

      expect(getMessage(task)).toBe('From exception object');
    });

    it('prefers "errors" to "error" and expects them to be an array in exception object', () => {
      const task = {
        variables: [
          {
            key: 'exception',
            value: {
              details: {
                errors: ['error 1', 'error 2'],
                error: 'From error',
              },
            },
          },
        ],
      };
      expect(getMessage(task)).toBe('error 1\n\nerror 2');
    });

    it('returns null if an exception variable is present but has no details', () => {
      const task = {
        variables: [
          {
            key: 'exception',
            value: 'i should be an object',
          },
        ],
      };
      expect(getMessage(task)).toBe(null);
    });

    it('falls back to extracting last orchestration message if no exception found in variables', () => {
      const task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                status: {
                  failed: true,
                },
                history: [{ status: 'i am fine' }, { status: 'i am terrible' }],
              },
            ],
          },
        ],
      };
      expect(getMessage(task)).toBe('i am terrible');
    });

    it('prefers message from kato exception object if present', () => {
      const task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                status: {
                  failed: true,
                },
                exception: {
                  message: 'I am the exception',
                },
                history: [{ status: 'i am terrible' }],
              },
            ],
          },
        ],
      };
      expect(getMessage(task)).toBe('I am the exception');
    });

    it('returns null if kato exception object does not have a message property', () => {
      const task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                exception: 'I am the problem',
                history: [{ status: 'i am terrible' }],
              },
            ],
          },
        ],
      };
      expect(getMessage(task)).toBe(null);
    });

    it('returns null if no kato exception and no history', () => {
      const task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                history: [] as any[],
              },
            ],
          },
        ],
      };
      expect(getMessage(task)).toBe(null);
    });

    it('gets orchestration message from failed kato task', () => {
      const task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                history: [{ status: 'i am the first' }],
              },
              {
                status: {
                  failed: true,
                },
                history: [{ status: 'i am the second' }],
              },
            ],
          },
        ],
      };
      expect(getMessage(task)).toBe('i am the second');
    });

    it('returns null if no failure message is present', () => {
      expect(getMessage({ status: 'SUCCEEDED' })).toBe(null);
    });

    it('links to the task holding an orchestration lock', () => {
      const href = jasmine.createSpy('href').and.returnValue('#/task');
      setDirectRouter({ stateService: { href } } as any);

      const message = getMessage({
        context: {
          exception: {
            exceptionType: 'LockFailureException',
            details: { currentLockValue: { application: 'app', id: 'task-id', type: 'orchestration' } },
          },
        },
      });

      expect(href).toHaveBeenCalledWith('home.applications.application.tasks.taskDetails', {
        application: 'app',
        taskId: 'task-id',
      });
      expect(message).toBe(
        'Failed to acquire lock. An <a href="#/task">existing task</a> is currently operating on the cluster.',
      );
    });

    it('links to the pipeline holding a pipeline lock', () => {
      const href = jasmine.createSpy('href').and.returnValue('#/pipeline');
      setDirectRouter({ stateService: { href } } as any);

      const message = getMessage({
        context: {
          exception: {
            exceptionType: 'LockFailureException',
            details: { currentLockValue: { application: 'app', id: 'execution-id', type: 'pipeline' } },
          },
        },
      });

      expect(href).toHaveBeenCalledWith('home.applications.application.pipelines.executionDetails.execution', {
        application: 'app',
        executionId: 'execution-id',
      });
      expect(message).toBe(
        'Failed to acquire lock. An <a href="#/pipeline">existing pipeline</a> is currently operating on the cluster.',
      );
    });
  });
});
