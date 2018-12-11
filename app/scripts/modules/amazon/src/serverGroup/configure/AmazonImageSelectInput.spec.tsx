import * as React from 'react';
import { mock, IHttpBackendService } from 'angular';
import { ShallowWrapper, ReactWrapper, shallow, mount } from 'enzyme';

import { IAmazonImage } from 'amazon/image';
import { Application } from 'core/application';
import { REACT_MODULE } from 'core/reactShims';
import { SETTINGS } from 'core/config';

import { AmazonImageSelectInput, IAmazonImageSelectorProps, IAmazonImageSelectorState } from './AmazonImageSelectInput';

const baseUrl = SETTINGS.gateUrl;

const application = new Application('testapp', null, []);
const region = 'us-region-1';
const credentials = 'prodaccount';
const imageName = 'fancypackage-1.0.0-h005.6c8b5fe-x86_64-20181206030728-xenial-hvm-sriov-ebs';
const amiId = 'fake-abcd123';

describe('<AmazonImageSelectInput/>', () => {
  let $httpBackend: IHttpBackendService;
  let shallowComponent: ShallowWrapper<IAmazonImageSelectorProps, IAmazonImageSelectorState>;
  let mountedComponent: ReactWrapper<IAmazonImageSelectorProps, IAmazonImageSelectorState>;

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject((_$httpBackend_: IHttpBackendService) => ($httpBackend = _$httpBackend_)));

  afterEach(() => {
    $httpBackend.verifyNoOutstandingExpectation();
    $httpBackend.verifyNoOutstandingRequest();
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
    it('using application name when no amiId is present in the initial value', () => {
      $httpBackend.expectGET(`${baseUrl}/images/find?q=testapp*`).respond(200, []);
      shallowComponent = shallow(<AmazonImageSelectInput {...baseProps} />);
      $httpBackend.flush();
    });

    it('and updates isLoadingPackageImages', () => {
      $httpBackend.expectGET(`${baseUrl}/images/find?q=testapp*`).respond(200, []);
      shallowComponent = shallow(<AmazonImageSelectInput {...baseProps} />);
      expect(shallowComponent.state().isLoadingPackageImages).toBe(true);
      $httpBackend.flush();
      expect(shallowComponent.state().isLoadingPackageImages).toBe(false);
    });

    it('using fetching image by amiId and looking up via the imageName', () => {
      const value = AmazonImageSelectInput.makeFakeImage(imageName, amiId, region);
      $httpBackend.expectGET(`${baseUrl}/images/${credentials}/${region}/${amiId}?provider=aws`).respond(200, [value]);
      $httpBackend.expectGET(`${baseUrl}/images/find?q=fancypackage-*`).respond(200, []);
      shallowComponent = shallow(<AmazonImageSelectInput {...baseProps} value={value} />);
      $httpBackend.flush();
    });

    it('using application name when an amiId is present in the initial value, but the image was not found', () => {
      const value = AmazonImageSelectInput.makeFakeImage(imageName, amiId, region);
      $httpBackend.expectGET(`${baseUrl}/images/${credentials}/${region}/${amiId}?provider=aws`).respond(200, null);
      $httpBackend.expectGET(`${baseUrl}/images/find?q=${application.name}*`).respond([]);
      shallowComponent = shallow(<AmazonImageSelectInput {...baseProps} value={value} />);
      $httpBackend.flush();
    });
  });

  it('calls onChange with the backend image when the package images are loaded', () => {
    const value = AmazonImageSelectInput.makeFakeImage(imageName, amiId, region);
    const backendValue = AmazonImageSelectInput.makeFakeImage(imageName, amiId, region);
    const onChange = jasmine.createSpy('onChange');
    $httpBackend
      .expectGET(`${baseUrl}/images/${credentials}/${region}/${amiId}?provider=aws`)
      .respond(200, [backendValue]);
    $httpBackend.expectGET(`${baseUrl}/images/find?q=fancypackage-*`).respond(200, [backendValue]);
    mountedComponent = mount(<AmazonImageSelectInput {...baseProps} onChange={onChange} value={value} />);
    $httpBackend.flush();

    expect(onChange).toHaveBeenCalledWith(backendValue);
  });

  it('calls onChange with null image when the image is not found in the package images', () => {
    const value = AmazonImageSelectInput.makeFakeImage(imageName, amiId, region);
    const noResults = [] as IAmazonImage[];
    $httpBackend.expectGET(`${baseUrl}/images/${credentials}/${region}/${amiId}?provider=aws`).respond(200, noResults);
    $httpBackend.expectGET(`${baseUrl}/images/find?q=testapp*`).respond(200, noResults);
    const onChange = jasmine.createSpy('onChange');
    mountedComponent = mount(<AmazonImageSelectInput {...baseProps} onChange={onChange} value={value} />);
    $httpBackend.flush();

    expect(onChange).toHaveBeenCalledWith(undefined);
  });
});
