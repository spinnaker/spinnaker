import {mock} from 'angular';

import {ORCHESTRATED_ITEM_TRANSFORMER, OrchestratedItemTransformer} from './orchestratedItem.transformer';

describe('orchestratedItem transformer', () => {
  let transformer: OrchestratedItemTransformer;

  beforeEach(mock.module(ORCHESTRATED_ITEM_TRANSFORMER));

  beforeEach(mock.inject((orchestratedItemTransformer: OrchestratedItemTransformer) => {
    transformer = orchestratedItemTransformer;
  }));

  describe('failure message extraction', () => {

    let getMessage = (obj: any) => {
      transformer.defineProperties(obj);
      return obj.failureMessage;
    };


    it('returns null when no stage context', () => {
      expect(getMessage({})).toBe(null);
    });

    it('returns null when no kato.tasks field in stage context', () => {
      expect(getMessage({context: {}})).toBe(null);
    });

    it('returns null when kato.tasks field in stage context is empty', () => {
      expect(getMessage({context: { 'kato.tasks': []}})).toBe(null);
    });

    it('returns null when last kato task has no exception', () => {
      let stage = {
        context: {
          'kato.tasks': [
            {
              exception: {
                message: 'failed!'
              }
            },
            {

            }
          ]
        }
      };
      expect(getMessage(stage)).toBe(null);
    });

    it('returns general exception if present', () => {
      expect(getMessage({context: { 'exception': { 'details' : { 'errors': ['E1', 'E2']}}}})).toBe('E1, E2');
      expect(getMessage({context: { 'exception': { 'details' : { 'errors': []}}}})).toBe(null);
      expect(getMessage({context: { }})).toBe(null);
    });

    it('returns general exception even if a kato task is present', () => {
      let stage = {
        context: {
          'kato.tasks': [
            {
              status: {
                failed: true,
              },
              exception: {
                message: 'failed!'
              }
            }
          ],
          exception: {
            details: {
              errors: ['E1', 'E2']
            }
          }
        }
      };
      expect(getMessage(stage)).toBe('E1, E2');
    });

    it('returns exception when it is in the last kato task', () => {
      let stage = {
        context: {
          'kato.tasks': [
            {
              message: 'this one is fine'
            },
            {
              status: {
                failed: true,
              },
              exception: {
                message: 'failed!'
              }
            }
          ]
        }
      };
      expect(getMessage(stage)).toBe('failed!');
    });

    it ('extracts exception object from variables', () => {
      let task = {
        variables: [
          {
            key: 'exception',
            value: {
              details: {
                error: 'From exception object'
              }
            }
          }
        ]
      };

      expect(getMessage(task)).toBe('From exception object');
    });

    it('prefers "errors" to "error" and expects them to be an array in exception object', () => {
      let task = {
        variables: [
          {
            key: 'exception',
            value: {
              details: {
                errors: [
                  'error 1',
                  'error 2'
                ],
                error: 'From error'
              }
            }
          }
        ]
      };
      expect(getMessage(task)).toBe('error 1, error 2');
    });

    it('returns null if an exception variable is present but has no details', () => {
      let task = {
        variables: [
          {
            key: 'exception',
            value: 'i should be an object'
          }
        ]
      };
      expect(getMessage(task)).toBe(null);
    });

    it('falls back to extracting last orchestration message if no exception found in variables', () => {
      let task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                status: {
                  failed: true,
                },
                history: [
                  { status: 'i am fine' },
                  { status: 'i am terrible' }
                ],
              }
            ]
          }
        ]
      };
      expect(getMessage(task)).toBe('i am terrible');
    });

    it('prefers message from kato exception object if present', () => {
      let task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                status: {
                  failed: true,
                },
                exception: {
                  message: 'I am the exception'
                },
                history: [
                  { status: 'i am terrible' }
                ],
              }
            ]
          }
        ]
      };
      expect(getMessage(task)).toBe('I am the exception');
    });

    it('returns null if kato exception object does not have a message property', () => {
      let task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                exception: 'I am the problem',
                history: [
                  { status: 'i am terrible' }
                ],
              }
            ]
          }
        ]
      };
      expect(getMessage(task)).toBe(null);
    });

    it('returns null if no kato exception and no history', () => {
      let task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                history: [] as any[],
              }
            ]
          }
        ]
      };
      expect(getMessage(task)).toBe(null);
    });

    it('gets orchestration message from failed kato task', () => {
      let task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                history: [
                  { status: 'i am the first' },
                ],
              },
              {
                status: {
                  failed: true,
                },
                history: [
                  { status: 'i am the second' },
                ],
              }
            ]
          }
        ]
      };
      expect(getMessage(task)).toBe('i am the second');
    });

    it('returns null if no failure message is present', () => {
      expect(getMessage({ status: 'SUCCEEDED' })).toBe(null);
    });
  });
});
