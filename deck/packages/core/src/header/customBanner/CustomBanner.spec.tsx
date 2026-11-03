import { act, render, screen, waitFor } from '@testing-library/react';
import { servicesPlugin, UIRouterReact } from '@uirouter/react';
import React from 'react';

import { CustomBannerComponent } from './CustomBanner';
import { getTestBannerConfigs } from '../../application/config/customBanner/CustomBannerConfig.spec';
import { ApplicationReader } from '../../application/service/ApplicationReader';

describe('<CustomBanner />', () => {
  let router: UIRouterReact;

  beforeEach(() => {
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.stateRegistry.register({ name: 'application', url: '/:application' });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    router.dispose();
  });

  const renderBanner = () =>
    render(<CustomBannerComponent router={router} stateParams={{}} stateService={router.stateService} />);

  const goToApplication = async () => {
    await act(async () => {
      await router.stateService.go('application', { application: 'my-app' }, { location: false });
    });
  };

  it('renders no banner by default', () => {
    const { container } = renderBanner();
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the enabled application banner with its colours and ignores disabled banners', async () => {
    const bannerConfigs = getTestBannerConfigs();
    vi.spyOn(ApplicationReader, 'getApplicationAttributes').mockResolvedValue({ customBanners: bannerConfigs });
    const { container } = renderBanner();

    await goToApplication();

    expect(await screen.findByText(bannerConfigs[0].text)).toBeInTheDocument();
    expect(screen.queryByText(bannerConfigs[1].text)).not.toBeInTheDocument();
    expect(container.querySelectorAll('.custom-banner')).toHaveLength(1);
    expect(container.querySelector('.custom-banner')).toHaveStyle({ color: bannerConfigs[0].textColor });
  });

  it('does not render when application attributes contain no enabled banner', async () => {
    vi.spyOn(ApplicationReader, 'getApplicationAttributes').mockResolvedValue({ customBanners: null });
    const { container } = renderBanner();

    await goToApplication();
    await waitFor(() => expect(ApplicationReader.getApplicationAttributes).toHaveBeenCalledWith('my-app'));

    expect(container).toBeEmptyDOMElement();
  });
});
