import { mock } from 'angular';
import type { ReactWrapper, ShallowWrapper } from 'enzyme';
import { mount, shallow } from 'enzyme';
import React from 'react';

import { Application } from '@spinnaker/core';
import { REACT_MODULE } from '@spinnaker/core';

import type { IAmazonImageSelectorProps, IAmazonImageSelectorState } from './AmazonImageSelectInput';
import { AmazonImageSelectInput } from './AmazonImageSelectInput';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import { mockHttpClient } from '../../../../core/src/api/mock/jasmine';
import type { IAmazonImage } from '../../image';
const application = new Application('testapp', null, []);
const region = 'us-region-1';
const credentials = 'prodaccount';
const imageName = 'fancypackage-1.0.0-h005.6c8b5fe-x86_64-20181206030728-xenial-hvm-sriov-ebs';
const amiId = 'fake-abcd123';

describe('<AmazonImageSelectInput/>', () => {
  let shallowComponent: ShallowWrapper<IAmazonImageSelectorProps, IAmazonImageSelectorState>;
  let mountedComponent: ReactWrapper<IAmazonImageSelectorProps, IAmazonImageSelectorState>;

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject());

  afterEach(() => {
    shallowComponent && shallowComponent.unmount();
    mountedComponent && mountedComponent.unmount();
  });

  const baseProps = {
    application,
    credentials,
    region,
    value: {} as IAmazonImage,
    onChange: () => null as any,
  };

  describe('fetches package images when mounted', () => {
    it('using application name when no amiId is present in the initial value', async () => {
      const http = mockHttpClient();
      http.expectGET(`/images/find?q=testapp*`).respond(200, []);
      shallowComponent = shallow(<AmazonImageSelectInput {...baseProps} />);
      await http.flush();
    });

    it('and updates isLoadingPackageImages', async () => {
      const http = mockHttpClient();
      http.expectGET(`/images/find?q=testapp*`).respond(200, []);
      shallowComponent = shallow(<AmazonImageSelectInput {...baseProps} />);
      expect(shallowComponent.state().isLoadingPackageImages).toBe(true);
      await http.flush();
      expect(shallowComponent.state().isLoadingPackageImages).toBe(false);
    });

    it('using fetching image by amiId and looking up via the imageName', async () => {
      const http = mockHttpClient();
      const value = AmazonImageSelectInput.makeFakeImage(imageName, amiId, region);
      http.expectGET(`/images/${credentials}/${region}/${amiId}?provider=aws`).respond(200, [value]);
      http.expectGET(`/images/find?q=fancypackage-*`).respond(200, []);
      shallowComponent = shallow(<AmazonImageSelectInput {...baseProps} value={value} />);
      await http.flush();
    });

    it('using application name when an amiId is present in the initial value, but the image was not found', async () => {
      const http = mockHttpClient();
      const value = AmazonImageSelectInput.makeFakeImage(imageName, amiId, region);
      http.expectGET(`/images/${credentials}/${region}/${amiId}?provider=aws`).respond(200, null);
      http.expectGET(`/images/find?q=${application.name}*`).respond(200, []);
      shallowComponent = shallow(<AmazonImageSelectInput {...baseProps} value={value} />);
      await http.flush();
    });
  });

  it('calls onChange with the backend image when the package images are loaded', async () => {
    const http = mockHttpClient();
    const value = AmazonImageSelectInput.makeFakeImage(imageName, amiId, region);
    const backendValue = AmazonImageSelectInput.makeFakeImage(imageName, amiId, region);
    const onChange = jasmine.createSpy('onChange');
    http.expectGET(`/images/${credentials}/${region}/${amiId}?provider=aws`).respond(200, [backendValue]);
    http.expectGET(`/images/find?q=fancypackage-*`).respond(200, [backendValue]);
    mountedComponent = mount(<AmazonImageSelectInput {...baseProps} onChange={onChange} value={value} />);
    await http.flush();

    expect(onChange).toHaveBeenCalledWith(backendValue);
  });

  it('calls onChange with null image when the image is not found in the package images', async () => {
    const http = mockHttpClient();
    const value = AmazonImageSelectInput.makeFakeImage(imageName, amiId, region);
    const noResults = [] as IAmazonImage[];
    http.expectGET(`/images/${credentials}/${region}/${amiId}?provider=aws`).respond(200, noResults);
    http.expectGET(`/images/find?q=testapp*`).respond(200, noResults);
    const onChange = jasmine.createSpy('onChange');
    mountedComponent = mount(<AmazonImageSelectInput {...baseProps} onChange={onChange} value={value} />);
    await http.flush();

    expect(onChange).toHaveBeenCalledWith(undefined);
  });
});
