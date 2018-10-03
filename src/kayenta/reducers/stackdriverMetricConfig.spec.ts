import { stackdriverMetricConfigReducer } from './stackdriverMetricConfig';
import * as Actions from 'kayenta/actions';

describe('Reducer: stackdriverMetricConfigReducer', () => {
  describe('update query field', () => {
    it('updates arbitrary query key/value pairs on a query', () => {
      const nextState = stackdriverMetricConfigReducer({ query: { crossSeriesReducer: 'REDUCE_NONE' } } as any, {
        type: Actions.UPDATE_STACKDRIVER_METRIC_QUERY_FIELD,
        payload: { field: 'crossSeriesReducer', value: 'REDUCE_MEAN' },
      });
      expect(nextState.query.crossSeriesReducer).toEqual('REDUCE_MEAN');
    });

    it('creates key/value pair if not already extant on the query', () => {
      const nextState = stackdriverMetricConfigReducer({ query: {} } as any, {
        type: Actions.UPDATE_STACKDRIVER_METRIC_QUERY_FIELD,
        payload: { field: 'crossSeriesReducer', value: 'REDUCE_MEAN' },
      });
      expect(nextState.query.crossSeriesReducer).toEqual('REDUCE_MEAN');
    });

    it('deletes key/value pair if passed falsy value', () => {
      [null, undefined, ''].forEach(falsyValue => {
        const nextState = stackdriverMetricConfigReducer({ query: { crossSeriesReducer: 'REDUCE_NONE' } } as any, {
          type: Actions.UPDATE_STACKDRIVER_METRIC_QUERY_FIELD,
          payload: { field: 'crossSeriesReducer', value: falsyValue },
        });
        expect(nextState.query.crossSeriesReducer).toBeUndefined();
      });
    });
  });
});
