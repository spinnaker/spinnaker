import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import type { Application } from '@spinnaker/core';

import { AmazonImageSelectInput } from './AmazonImageSelectInput';
import { setupUser } from '../../../../core/src/utils/testUtils/userEvent';
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
    vi.spyOn(AwsImageReader.prototype, 'findImages').mockImplementation(({ q }: { q: string }) =>
      Promise.resolve(q === 'app-package-2' ? [image2] : [image1, image2]),
    );
    vi.spyOn(AwsImageReader.prototype, 'getImage').mockReturnValue(Promise.resolve(null));
  });

  function renderInput(onChange: (image: IAmazonImage) => void, value: IAmazonImage = null) {
    return render(
      <AmazonImageSelectInput
        onChange={onChange}
        value={value}
        application={application}
        credentials="test"
        region="us-east-1"
      />,
    );
  }

  async function waitForPackageImages(): Promise<void> {
    expect(await screen.findByText('Pick an image')).toBeInTheDocument();
  }

  function openMenu(container: HTMLElement): void {
    fireEvent.mouseDown(container.querySelector('.Select-control'));
  }

  it('renders a single options menu wrapper, not a nested duplicate', async () => {
    const { container } = renderInput(vi.fn());
    await waitForPackageImages();
    openMenu(container);

    // Regression test: buildImageMenu used to re-wrap its options in a second
    // ".Select-menu-outer > .Select-menu" pair on top of react-select's own wrapper. The inner
    // duplicate kept its default `position: absolute` styling instead of the `position: static`
    // override TetheredSelect applies to the outermost wrapper, which broke the sizing/hit-testing
    // of the real, Tether-positioned menu: the list was visible but clicks landed on nothing.
    expect(await screen.findAllByRole('option')).toHaveLength(2);
    expect(document.querySelectorAll('.Select-menu-outer')).toHaveLength(1);
  });

  it('selects the clicked image from the package images dropdown', async () => {
    const onChange = vi.fn();
    const { container } = renderInput(onChange);
    await waitForPackageImages();
    openMenu(container);

    const options = await screen.findAllByRole('option');
    expect(options).toHaveLength(2);
    fireEvent.mouseDown(screen.getByRole('option', { name: new RegExp(image1.imageName) }));

    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ imageName: image1.imageName }));
  });

  it('provides a way back from "Search All Images" to the package images dropdown', async () => {
    const user = setupUser();
    renderInput(vi.fn());
    await waitForPackageImages();

    await user.click(screen.getByRole('button', { name: 'Search All Images' }));
    expect(screen.getByText('Search for an image...')).toBeInTheDocument();

    // Regression test: there used to be no control to switch back out of search-all-images mode.
    await user.click(screen.getByRole('button', { name: 'Back to Package Images' }));

    expect(screen.getByText('Pick an image')).toBeInTheDocument();
  });

  it('selects the clicked image while searching all images', async () => {
    const user = setupUser();
    const onChange = vi.fn();
    const { container } = renderInput(onChange);
    await waitForPackageImages();

    await user.click(screen.getByRole('button', { name: 'Search All Images' }));
    await user.type(screen.getByRole('combobox'), 'app-package-2');

    await waitFor(() => expect(screen.getAllByRole('option')).toHaveLength(1), { timeout: 2000 });
    expect(container.querySelector('.Select')).toHaveClass('is-open');
    fireEvent.mouseDown(screen.getByRole('option', { name: new RegExp(image2.imageName) }));

    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ imageName: image2.imageName }));
  });
});
