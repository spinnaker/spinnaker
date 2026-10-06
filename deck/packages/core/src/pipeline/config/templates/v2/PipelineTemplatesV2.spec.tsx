import { shallow } from 'enzyme';
import React from 'react';

import { PipelineTemplatesV2Component } from './PipelineTemplatesV2';
import { PipelineTemplateReader } from '../PipelineTemplateReader';

describe('PipelineTemplatesV2', () => {
  it('initializes the selected template from injected route params', () => {
    const component = shallow(
      <PipelineTemplatesV2Component
        {...({
          router: {},
          stateParams: { templateId: 'injected-template' },
          stateService: {},
        } as any)}
      />,
      { disableLifecycleMethods: true },
    );

    expect(component.state('viewTemplateVersion')).toBe('injected-template');
  });

  it('dismisses template details through the injected state service', () => {
    const injectedGo = vi.fn();
    const component = shallow(
      <PipelineTemplatesV2Component {...({ router: {}, stateParams: {}, stateService: { go: injectedGo } } as any)} />,
      { disableLifecycleMethods: true },
    );

    (component.instance() as any).dismissDetailsModal();

    expect(injectedGo).toHaveBeenCalledWith('home.pipeline-templates');
  });

  it('observes route changes through the injected router', () => {
    const injectedUnsubscribe = vi.fn();
    const injectedOnSuccess = vi.fn().mockReturnValue(injectedUnsubscribe);
    vi.spyOn(PipelineTemplateReader, 'getV2PipelineTemplateList').mockReturnValue(Promise.resolve({}));
    const component = shallow(
      <PipelineTemplatesV2Component
        {...({
          router: { transitionService: { onSuccess: injectedOnSuccess } },
          stateParams: {},
          stateService: {},
        } as any)}
      />,
      { disableLifecycleMethods: true },
    );
    const instance = component.instance() as PipelineTemplatesV2Component;

    instance.componentDidMount();
    instance.componentWillUnmount();

    expect(injectedOnSuccess).toHaveBeenCalledWith({}, expect.any(Function));
    expect(injectedUnsubscribe).toHaveBeenCalled();
  });
});
