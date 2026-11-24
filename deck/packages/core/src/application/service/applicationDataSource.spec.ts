import type { Application } from '../application.model';
import { ApplicationDataSource } from './applicationDataSource';

describe('ApplicationDataSource refresh subscriptions', () => {
  function createDataSource(): ApplicationDataSource<string[]> {
    return new ApplicationDataSource({ key: 'example', defaultData: [] }, { name: 'app' } as Application);
  }

  function emitError(dataSource: ApplicationDataSource<string[]>, error: Error): void {
    dataSource.status$.next({
      status: 'ERROR',
      loaded: false,
      error,
      lastRefresh: 0,
      data: dataSource.data,
    });
  }

  it('notifies every-refresh callbacks without a lifecycle object until explicitly unsubscribed', () => {
    const dataSource = createDataSource();
    const onRefresh = vi.fn();
    const onError = vi.fn();
    const unsubscribe = dataSource.onRefresh(onRefresh, onError);
    const refreshError = new Error('refresh failed');

    dataSource.data$.next(['first']);
    emitError(dataSource, refreshError);

    expect(onRefresh).toHaveBeenCalledExactlyOnceWith(['first']);
    expect(onError).toHaveBeenCalledExactlyOnceWith(refreshError);

    unsubscribe();
    dataSource.data$.next(['second']);
    emitError(dataSource, refreshError);

    expect(onRefresh).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledTimes(1);
  });

  it('notifies next-refresh callbacks without a lifecycle object and honors explicit unsubscribe', () => {
    const dataSource = createDataSource();
    const onRefresh = vi.fn();
    const onError = vi.fn();

    dataSource.onNextRefresh(onRefresh, onError);
    dataSource.data$.next(['first']);
    expect(onRefresh).toHaveBeenCalledExactlyOnceWith(['first']);

    dataSource.onNextRefresh(onRefresh, onError);
    const refreshError = new Error('refresh failed');
    emitError(dataSource, refreshError);
    expect(onError).toHaveBeenCalledExactlyOnceWith(refreshError);

    const unsubscribe = dataSource.onNextRefresh(onRefresh, onError);
    unsubscribe();
    dataSource.data$.next(['second']);
    emitError(dataSource, refreshError);

    expect(onRefresh).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledTimes(1);
  });
});
