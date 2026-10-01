import { shallow } from 'enzyme';
import React from 'react';

import { ApplicationsComponent } from './Applications';
import { CreateApplicationModal } from '../modal/CreateApplicationModal';
import { ApplicationReader } from '../service/ApplicationReader';

describe('Applications create deep link', () => {
  const renderApplications = (stateParams: Record<string, any>, go = vi.fn()) => ({
    go,
    wrapper: shallow(
      <ApplicationsComponent router={{} as any} stateParams={stateParams} stateService={{ go } as any} />,
    ),
  });

  it('opens the direct modal and routes to the created application', async () => {
    vi.spyOn(ApplicationReader, 'listApplications').mockReturnValue(Promise.resolve([]));
    vi.spyOn(CreateApplicationModal, 'show').mockReturnValue(Promise.resolve({ name: 'myapp' }) as any);

    const { go, wrapper } = renderApplications({ create: 'myapp' });
    await Promise.resolve();
    await Promise.resolve();

    expect(CreateApplicationModal.show).toHaveBeenCalledWith('myapp');
    expect(go).toHaveBeenCalledWith('home.applications.application', { application: 'myapp', create: null });
    wrapper.unmount();
  });

  it('clears the create query parameter when the direct modal is dismissed', async () => {
    vi.spyOn(ApplicationReader, 'listApplications').mockReturnValue(Promise.resolve([]));
    vi.spyOn(CreateApplicationModal, 'show').mockReturnValue(Promise.reject('cancel'));

    const { go, wrapper } = renderApplications({ create: 'myapp' });
    await Promise.resolve();
    await Promise.resolve();

    expect(go).toHaveBeenCalledWith('home.applications', { create: null });
    wrapper.unmount();
  });
});
