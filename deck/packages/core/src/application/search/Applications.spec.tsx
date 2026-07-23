import { shallow } from 'enzyme';
import React from 'react';

import { Applications } from './Applications';
import { CreateApplicationModal } from '../modal/CreateApplicationModal';
import { ApplicationReader } from '../service/ApplicationReader';
import { AngularServices } from '../../angular/services';

describe('Applications create deep link', () => {
  it('opens the direct modal and routes to the created application', async () => {
    const go = jasmine.createSpy('go');
    spyOnProperty(AngularServices, '$stateParams', 'get').and.returnValue({ create: 'myapp' } as any);
    spyOnProperty(AngularServices, '$state', 'get').and.returnValue({ go } as any);
    spyOn(ApplicationReader, 'listApplications').and.returnValue(Promise.resolve([]));
    spyOn(CreateApplicationModal, 'show').and.returnValue(Promise.resolve({ name: 'myapp' }) as any);

    const wrapper = shallow(<Applications />);
    await Promise.resolve();
    await Promise.resolve();

    expect(CreateApplicationModal.show).toHaveBeenCalledWith('myapp');
    expect(go).toHaveBeenCalledWith('home.applications.application', { application: 'myapp', create: null });
    wrapper.unmount();
  });

  it('clears the create query parameter when the direct modal is dismissed', async () => {
    const go = jasmine.createSpy('go');
    spyOnProperty(AngularServices, '$stateParams', 'get').and.returnValue({ create: 'myapp' } as any);
    spyOnProperty(AngularServices, '$state', 'get').and.returnValue({ go } as any);
    spyOn(ApplicationReader, 'listApplications').and.returnValue(Promise.resolve([]));
    spyOn(CreateApplicationModal, 'show').and.returnValue(Promise.reject('cancel'));

    const wrapper = shallow(<Applications />);
    await Promise.resolve();
    await Promise.resolve();

    expect(go).toHaveBeenCalledWith('home.applications', { create: null });
    wrapper.unmount();
  });
});
