import type { ReactWrapper } from 'enzyme';
import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import type { Application, TetheredSelect as TetheredSelectType } from '@spinnaker/core';
import { TetheredSelect } from '@spinnaker/core';

import { AmazonImageSelectInput } from './AmazonImageSelectInput';
import type { IAmazonImage } from '../../image';
import { AwsImageReader } from '../../image';

function makeImage(imageName: string, amiId: string, region = 'us-east-1'): IAmazonImage {
  return {
    imageName,
    amis: { [region]: [amiId] },
    attributes: { virtualizationType: 'hvm', architecture: 'x86_64', creationDate: '2024-01-01T00:00:00.000Z' },
  } as IAmazonImage;
}

describe('AmazonImageSelectInput', () => {
  const application = ({ name: 'app' } as unknown) as Application;
  const image1 = makeImage('app-package-1.0', 'ami-111');
  const image2 = makeImage('app-package-2.0', 'ami-222');

  beforeEach(() => {
    spyOn(AwsImageReader.prototype, 'findImages').and.returnValue(Promise.resolve([image1, image2]));
    spyOn(AwsImageReader.prototype, 'getImage').and.returnValue(Promise.resolve(null));
  });

  async function settle(component: ReactWrapper): Promise<void> {
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    component.update();
  }

  // The package-images dropdown's menu is only rendered by the underlying react-select
  // instance once it's open, which normally happens via a real focus/mousedown DOM event.
  // Driving the TetheredSelect instance directly is more reliable than simulating focus in a
  // detached test DOM, and it's how a user's click on the control ultimately manifests anyway.
  function openMenu(component: ReactWrapper): void {
    (component.find(TetheredSelect).instance() as TetheredSelectType).setState({ isOpen: true });
    component.update();
  }

  function mountInput(onChange: (image: IAmazonImage) => void, value: IAmazonImage = null) {
    return mount(
      <AmazonImageSelectInput
        onChange={onChange}
        value={value}
        application={application}
        credentials="test"
        region="us-east-1"
      />,
    );
  }

  it('renders a single options menu wrapper, not a nested duplicate', async () => {
    const component = mountInput(jasmine.createSpy('onChange'));
    await settle(component);
    openMenu(component);

    // Regression test: buildImageMenu used to re-wrap its options in a second
    // ".Select-menu-outer > .Select-menu" pair on top of react-select's own wrapper. The inner
    // duplicate kept its default `position: absolute` styling instead of the `position: static`
    // override TetheredSelect applies to the outermost wrapper, which broke the sizing/hit-testing
    // of the real, Tether-positioned menu: the list was visible but clicks landed on nothing.
    expect(component.find('.Select-menu-outer').length).toBe(1);
  });

  it('selects the clicked image from the package images dropdown', async () => {
    const onChange = jasmine.createSpy('onChange');
    const component = mountInput(onChange);
    await settle(component);
    openMenu(component);

    const options = component.find('.Select-option');
    expect(options.length).toBe(2);
    options.first().simulate('mousedown');

    expect(onChange).toHaveBeenCalledWith(jasmine.objectContaining({ imageName: image1.imageName }));
  });

  it('provides a way back from "Search All Images" to the package images dropdown', async () => {
    const component = mountInput(jasmine.createSpy('onChange'));
    await settle(component);

    expect(component.text()).toContain('Pick an image');

    component.find('button.link').simulate('click');
    component.update();
    expect(component.text()).toContain('Search for an image');

    // Regression test: there used to be no control to switch back out of search-all-images mode.
    const backButton = component.find('button.link');
    expect(backButton.text()).toContain('Back to Package Images');
    backButton.simulate('click');
    component.update();

    expect(component.text()).toContain('Pick an image');
  });

  it('selects the clicked image while searching all images', async () => {
    const onChange = jasmine.createSpy('onChange');
    const component = mountInput(onChange);
    await settle(component);

    component.find('button.link').simulate('click');
    component.update();

    // Populate search results directly, bypassing the debounced RxJS search pipeline, which is
    // not what this test is exercising.
    (component.instance() as AmazonImageSelectInput).setState({ searchString: 'app', searchResults: [image2] });
    component.update();
    openMenu(component);

    const options = component.find('.Select-option');
    expect(options.length).toBe(1);
    options.first().simulate('mousedown');

    expect(onChange).toHaveBeenCalledWith(jasmine.objectContaining({ imageName: image2.imageName }));
  });
});
