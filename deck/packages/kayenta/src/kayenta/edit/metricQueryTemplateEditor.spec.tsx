import { mount } from 'enzyme';
import * as React from 'react';
import { Provider } from 'react-redux';
import { createStore } from 'redux';

import { MetricQueryTemplateEditor } from './metricQueryTemplateEditor';
import type { ITemplateProviderVariables } from './templateProviderVariables';

describe('MetricQueryTemplateEditor', () => {
  const providerVariableHints: ITemplateProviderVariables = {
    variables: ['scope', 'location'],
    example: 'rate(http_requests_total{service="${scope}", region="${location}"}[5m])',
  };

  // DisableableTextarea is itself connected to redux (to check the app-wide disableConfigEdit
  // flag), so it needs a Provider in the tree even though this component's own props are passed
  // in directly rather than through connect().
  const store = createStore(() => ({ app: { disableConfigEdit: false } }));

  const buildComponent = (props: Partial<React.ComponentProps<typeof MetricQueryTemplateEditor>> = {}) => {
    const editInlineTemplate = jasmine.createSpy('editInlineTemplate');
    const transformValueForSave = (value: string) => value;
    const component = mount(
      <Provider store={store}>
        <MetricQueryTemplateEditor
          providerVariableHints={providerVariableHints}
          inlineTemplateValue=""
          transformValueForSave={transformValueForSave}
          editInlineTemplate={editInlineTemplate}
          {...props}
        />
      </Provider>,
    );
    return { component, editInlineTemplate };
  };

  it('pre-fills the textarea with the provider example when the metric has no template yet', () => {
    const { component } = buildComponent({ inlineTemplateValue: '' });
    const textarea = component.find('textarea');
    expect(textarea.prop('value')).toEqual(providerVariableHints.example);
  });

  it('does not dispatch anything just from rendering with an empty template', () => {
    const { editInlineTemplate } = buildComponent({ inlineTemplateValue: '' });
    expect(editInlineTemplate).not.toHaveBeenCalled();
  });

  it('shows the real value, not the example, once the metric already has a template', () => {
    const { component, editInlineTemplate } = buildComponent({ inlineTemplateValue: 'existing template text' });
    const textarea = component.find('textarea');
    expect(textarea.prop('value')).toEqual('existing template text');
    expect(editInlineTemplate).not.toHaveBeenCalled();
  });

  it('dispatches the edited value via editInlineTemplate when the user types', () => {
    const { component, editInlineTemplate } = buildComponent({ inlineTemplateValue: '' });
    component.find('textarea').simulate('change', { target: { value: 'my custom query' } });
    expect(editInlineTemplate).toHaveBeenCalledWith('my custom query');
  });

  it('shows a "Template is required" error when there is no example and no value', () => {
    const { component } = buildComponent({ inlineTemplateValue: '', providerVariableHints: undefined });
    expect(component.text()).toContain('Template is required');
  });

  it('does not render a "Saved Templates" dropdown', () => {
    const { component } = buildComponent();
    expect(component.find('Select').length).toEqual(0);
    expect(component.text()).not.toContain('Saved Templates');
  });

  it('does not render a "Save as reusable template" button', () => {
    const { component } = buildComponent();
    expect(component.find('button').length).toEqual(0);
    expect(component.text()).not.toContain('Save as reusable template');
  });

  it('renders the provider variable hint line', () => {
    const { component } = buildComponent();
    expect(component.text()).toContain('Available variables: ${scope}, ${location}');
  });
});
