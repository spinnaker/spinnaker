import { waitFor } from '@testing-library/react';
import React from 'react';

import { ApplicationsComponent } from './Applications';
import { createDeckRuntime } from '../../bootstrap/DeckRuntime';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { CreateApplicationModal } from '../modal/CreateApplicationModal';
import { ApplicationReader } from '../service/ApplicationReader';
import { renderWithRouter } from '../../utils/testUtils/rtl';

describe('Applications create deep link', () => {
  const renderApplications = (stateParams: Record<string, any>, go = vi.fn()) => {
    const runtime = createDeckRuntime();
    const rendered = renderWithRouter(
      <DeckRuntimeContext.Provider value={runtime}>
        <ApplicationsComponent router={{} as any} stateParams={stateParams} stateService={{ go } as any} />
      </DeckRuntimeContext.Provider>,
    );
    return {
      go,
      ...rendered,
      unmount: () => {
        rendered.unmount();
        runtime.dispose();
      },
    };
  };

  it('opens the direct modal and routes to the created application', async () => {
    vi.spyOn(ApplicationReader, 'listApplications').mockReturnValue(Promise.resolve([]));
    vi.spyOn(CreateApplicationModal, 'show').mockReturnValue(Promise.resolve({ name: 'myapp' }) as any);

    const { go, unmount } = renderApplications({ create: 'myapp' });

    await waitFor(() => expect(CreateApplicationModal.show).toHaveBeenCalledWith('myapp'));
    await waitFor(() =>
      expect(go).toHaveBeenCalledWith('home.applications.application', { application: 'myapp', create: null }),
    );
    unmount();
  });

  it('clears the create query parameter when the direct modal is dismissed', async () => {
    vi.spyOn(ApplicationReader, 'listApplications').mockReturnValue(Promise.resolve([]));
    vi.spyOn(CreateApplicationModal, 'show').mockReturnValue(Promise.reject('cancel'));

    const { go, unmount } = renderApplications({ create: 'myapp' });

    await waitFor(() => expect(go).toHaveBeenCalledWith('home.applications', { create: null }));
    unmount();
  });
});
