'use strict';

describe('orchestratedItem transformer', function () {
  var transformer;

  beforeEach(window.module(
    require('./orchestratedItem.transformer')
  ));

  beforeEach(window.inject(function(orchestratedItemTransformer) {
    transformer = orchestratedItemTransformer;
  }));

  describe('failure message extraction', function () {

    let getMessage = (obj) => {
      transformer.defineProperties(obj);
      return obj.failureMessage;
    };


    it('returns null when no stage context', function() {
      expect(getMessage({})).toBe(null);
    });

    it('returns null when no kato.tasks field in stage context', function() {
      expect(getMessage({context: {}})).toBe(null);
    });

    it('returns null when kato.tasks field in stage context is empty', function() {
      expect(getMessage({context: { 'kato.tasks': []}})).toBe(null);
    });

    it('returns null when last kato task has no exception', function() {
      var stage = {
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

    it('returns general exception if present', function() {
      expect(getMessage({context: { 'exception': { 'details' : { 'errors': ['E1', 'E2']}}}})).toBe('E1, E2');
      expect(getMessage({context: { 'exception': { 'details' : { 'errors': []}}}})).toBe(null);
      expect(getMessage({context: { }})).toBe(null);
    });

    it('returns general exception even if a kato task is present', function() {
      var stage = {
        context: {
          'kato.tasks': [
            {
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

    it('returns exception when it is in the last kato task', function() {
      var stage = {
        context: {
          'kato.tasks': [
            {
              message: 'this one is fine'
            },
            {
              exception: {
                message: 'failed!'
              }
            }
          ]
        }
      };
      expect(getMessage(stage)).toBe('failed!');
    });

    it ('extracts exception object from variables', function () {
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

    it('prefers "errors" to "error" and expects them to be an array in exception object', function () {
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

    it('returns null if an exception variable is present but has no details', function () {
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

    it('falls back to extracting last orchestration message if no exception found in variables', function () {
      let task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
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

    it('prefers message from kato exception object if present', function () {
      let task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
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

    it('returns null if kato exception object does not have a message property', function () {
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

    it('returns null if no kato exception and no history', function () {
      let task = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                history: [],
              }
            ]
          }
        ]
      };
      expect(getMessage(task)).toBe(null);
    });

    it('gets orchestration message from last kato task', function () {
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

    it('returns null if no failure message is present', function () {
      expect(getMessage({ status: 'SUCCEEDED' })).toBe(null);
    });

    it('returns null if tide task succeeded', function () {
      let task = {
        variables: [
          {
            key: 'tide.task',
            value: {
              taskComplete: { status: 'success', message: 'the message' }
            }
          }
        ]
      };
      expect(getMessage(task)).toBe(null);
      task.variables[0].value.taskComplete.status = 'failure';

      expect(getMessage(task)).toBe('the message');

    });

    it('prefers task exception over tide exception if present', function () {
      let task = {
        variables: [
          {
            key: 'exception',
            value: {
              details: {
                error: 'From exception object'
              }
            }
          },
          {
            key: 'tide.task',
            value: {
              taskComplete: { status: 'success', message: 'the message' }
            }
          }
        ]
      };
      expect(getMessage(task)).toBe('From exception object');
    });




  });
});
