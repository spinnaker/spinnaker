import React from 'react';
import { RecoilRoot } from 'recoil';

import { ApplicationComponent } from './ApplicationComponent';
import type { Application } from './application.model';
import { renderWithRouter } from '../utils/testUtils/rtl';

describe('<ApplicationComponent />', () => {
  it('does not remount the same app when child route props refresh', () => {
    const unsubscribeRefresh = vi.fn();
    const app = ({
      attributes: { dataSources: [] },
      dataSources: [],
      disableAutoRefresh: vi.fn(),
      enableAutoRefresh: vi.fn(),
      name: 'kubernetesapp',
      subscribeToRefresh: vi.fn().mockReturnValue(unsubscribeRefresh),
      getDataSource: vi.fn().mockReturnValue({ data: [], onRefresh: () => () => undefined }),
      onRefresh: vi.fn().mockReturnValue(() => undefined),
    } as unknown) as Application;

    const { rerender, unmount } = renderWithRouter(
      <RecoilRoot>
        <ApplicationComponent app={app} />
      </RecoilRoot>,
    );

    expect(app.enableAutoRefresh).toHaveBeenCalledTimes(1);

    rerender(
      <RecoilRoot>
        <ApplicationComponent app={app} />
      </RecoilRoot>,
    );

    expect(app.disableAutoRefresh).not.toHaveBeenCalled();
    expect(app.enableAutoRefresh).toHaveBeenCalledTimes(1);

    unmount();

    expect(unsubscribeRefresh).toHaveBeenCalledTimes(1);
    expect(app.disableAutoRefresh).toHaveBeenCalledTimes(1);
  });
});
