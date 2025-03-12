import * as Actions from 'kayenta/actions';

import { prometheusMetricConfigReducer } from './prometheusMetricConfig';

describe('Reducer: prometheusMetricConfigReducer', () => {
  describe('update query field', () => {
    it('updates arbitrary query key/value pairs on a query', () => {
      const nextState = prometheusMetricConfigReducer({ query: { resourceType: 'aws_ec2_instance' } } as any, {
        type: Actions.UPDATE_PROMETHEUS_METRIC_QUERY_FIELD,
        payload: { field: 'resourceType', value: 'gce_instance' },
      });
      expect(nextState.query.resourceType).toEqual('gce_instance');
    });

    it('creates key/value pair if not already extant on the query', () => {
      const nextState = prometheusMetricConfigReducer({ query: {} } as any, {
        type: Actions.UPDATE_PROMETHEUS_METRIC_QUERY_FIELD,
        payload: { field: 'resourceType', value: 'gce_instance' },
      });
      expect(nextState.query.resourceType).toEqual('gce_instance');
    });

    it('deletes key/value pair if passed falsy value', () => {
      [null, undefined, ''].forEach((falsyValue) => {
        const nextState = prometheusMetricConfigReducer({ query: { resourceType: 'aws_ec2_instance' } } as any, {
          type: Actions.UPDATE_PROMETHEUS_METRIC_QUERY_FIELD,
          payload: { field: 'resourceType', value: falsyValue },
        });
        expect(nextState.query.resourceType).toBeUndefined();
      });
    });
  });
});
