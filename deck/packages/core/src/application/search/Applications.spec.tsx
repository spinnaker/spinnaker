import { shallow } from 'enzyme';
import React from 'react';

import { ApplicationsComponent } from './Applications';
import { CreateApplicationModal } from '../modal/CreateApplicationModal';
import { ApplicationReader } from '../service/ApplicationReader';

describe('Applications create deep link', () => {
  const renderApplications = (stateParams: Record<string, any>, go = jasmine.createSpy('go')) => ({
    go,
    wrapper: shallow(
      <ApplicationsComponent router={{} as any} stateParams={stateParams} stateService={{ go } as any} />,
    ),
  });

  it('opens the direct modal and routes to the created application', async () => {
    spyOn(ApplicationReader, 'listApplications').and.returnValue(Promise.resolve([]));
    spyOn(CreateApplicationModal, 'show').and.returnValue(Promise.resolve({ name: 'myapp' }) as any);

    const { go, wrapper } = renderApplications({ create: 'myapp' });
    await Promise.resolve();
    await Promise.resolve();

    expect(CreateApplicationModal.show).toHaveBeenCalledWith('myapp');
    expect(go).toHaveBeenCalledWith('home.applications.application', { application: 'myapp', create: null });
    wrapper.unmount();
  });

  it('clears the create query parameter when the direct modal is dismissed', async () => {
    spyOn(ApplicationReader, 'listApplications').and.returnValue(Promise.resolve([]));
    spyOn(CreateApplicationModal, 'show').and.returnValue(Promise.reject('cancel'));

    const { go, wrapper } = renderApplications({ create: 'myapp' });
    await Promise.resolve();
    await Promise.resolve();

    expect(go).toHaveBeenCalledWith('home.applications', { create: null });
    wrapper.unmount();
  });
});
