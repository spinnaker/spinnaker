import { ApplicationDataSource, IFetchStatus } from '../../application';
import { useLatestCallback } from './useLatestCallback.hook';
import { useObservableValue } from './useObservableValue.hook';

export interface IDataSourceResult<T> extends IFetchStatus {
  data: T;
  refresh: () => void;
}

/**
 * A react hook that returns the current value an ApplicationDataSource
 * and triggers a re-render when a new value is set. Also provides a function
 * for refreshing the data source manually.
 *
 * @param dataSource the data source to subscribe to
 * @returns IDataSourceResult<T>
 */
export const useDataSource = <T>(dataSource: ApplicationDataSource<T>): IDataSourceResult<T> => {
  const status = useObservableValue(dataSource.status$, dataSource.status$.value);

  // Memoize to give consumers a stable ref,
  // but don't return the promise that dataSource.refresh() returns
  const refresh = useLatestCallback(() => {
    dataSource.refresh();
  });

  return {
    ...status,
    refresh,
  };
};
