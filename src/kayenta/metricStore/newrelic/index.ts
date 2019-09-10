import metricStoreConfigStore from '../metricStoreConfig.service';
import { queryFinder } from './metricConfigurer';

metricStoreConfigStore.register({
  name: 'newrelic',
  metricConfigurer: null,
  queryFinder,
});
