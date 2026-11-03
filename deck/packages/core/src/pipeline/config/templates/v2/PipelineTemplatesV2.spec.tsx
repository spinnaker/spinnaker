import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact, UIViewContext } from '@uirouter/react';
import { UIRouterRxPlugin } from '@uirouter/rx';
import { render, screen, waitFor } from '@testing-library/react';
import { setupUser } from '../../../../utils/testUtils/userEvent';
import React from 'react';

import { PipelineTemplatesV2Component } from './PipelineTemplatesV2';
import { PipelineTemplateReader } from '../PipelineTemplateReader';

describe('PipelineTemplatesV2', () => {
  const template = {
    id: 'injected-template',
    metadata: { name: 'Injected Template', owner: 'owner@example.com' },
    pipeline: {},
    schema: 'v2',
    updateTs: '0',
    variables: [],
  } as any;

  const createRouter = async () => {
    const router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    router.plugin(UIRouterRxPlugin);
    router.stateRegistry.register({ name: 'home', url: '/' });
    router.stateRegistry.register({ name: 'home.pipeline-templates', url: 'templates' });
    router.stateRegistry.register({
      name: 'home.pipeline-templates.pipeline-templates-detail',
      url: '/:templateId',
    });
    await router.stateService.go('home.pipeline-templates', {}, { location: false });
    return router;
  };

  const renderComponent = (router: UIRouterReact, stateService = { go: vi.fn() }) =>
    render(
      <UIRouterContext.Provider value={router}>
        <UIViewContext.Provider
          value={{ fqn: '', context: router.stateRegistry.get('home.pipeline-templates').$$state() } as any}
        >
          <PipelineTemplatesV2Component
            {...({ router, stateParams: { templateId: 'injected-template' }, stateService } as any)}
          />
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );

  it('initializes the selected template from injected route params', async () => {
    const router = await createRouter();
    vi.spyOn(PipelineTemplateReader, 'getV2PipelineTemplateList').mockResolvedValue({
      'injected-template': [template],
    });
    const { unmount } = renderComponent(router);

    expect(await screen.findByRole('heading', { name: 'View Pipeline Template' })).toBeVisible();
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('Injected Template');
    await new Promise((resolve) => setTimeout(resolve, 50));
    unmount();
    router.dispose();
  });

  it('dismisses template details through the injected state service', async () => {
    const user = setupUser();
    const router = await createRouter();
    const injectedGo = vi.fn();
    vi.spyOn(PipelineTemplateReader, 'getV2PipelineTemplateList').mockResolvedValue({
      'injected-template': [template],
    });
    const { unmount } = renderComponent(router, { go: injectedGo });

    await screen.findByRole('heading', { name: 'View Pipeline Template' });
    await user.click(screen.getByRole('button', { name: 'Close' }));

    expect(injectedGo).toHaveBeenCalledWith('home.pipeline-templates');
    await new Promise((resolve) => setTimeout(resolve, 50));
    unmount();
    router.dispose();
  });

  it('observes route changes through the injected router', async () => {
    const router = await createRouter();
    const injectedUnsubscribe = vi.fn();
    const injectedOnSuccess = vi.spyOn(router.transitionService, 'onSuccess').mockReturnValue(injectedUnsubscribe);
    vi.spyOn(PipelineTemplateReader, 'getV2PipelineTemplateList').mockReturnValue(Promise.resolve({}));
    const { unmount } = renderComponent(router);
    await waitFor(() => expect(injectedOnSuccess).toHaveBeenCalled());
    unmount();

    expect(injectedOnSuccess).toHaveBeenCalledWith({}, expect.any(Function));
    expect(injectedUnsubscribe).toHaveBeenCalled();
    router.dispose();
  });
});
