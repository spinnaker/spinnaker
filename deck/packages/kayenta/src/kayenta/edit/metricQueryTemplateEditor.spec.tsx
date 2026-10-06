import { fireEvent, render, screen } from '@testing-library/react';
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
    const editInlineTemplate = vi.fn();
    const transformValueForSave = (value: string) => value;
    render(
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
    return { editInlineTemplate };
  };

  it('pre-fills the textarea with the provider example when the metric has no template yet', () => {
    buildComponent({ inlineTemplateValue: '' });
    expect(screen.getByRole('textbox')).toHaveValue(providerVariableHints.example);
  });

  it('does not dispatch anything just from rendering with an empty template', () => {
    const { editInlineTemplate } = buildComponent({ inlineTemplateValue: '' });
    expect(editInlineTemplate).not.toHaveBeenCalled();
  });

  it('shows the real value, not the example, once the metric already has a template', () => {
    const { editInlineTemplate } = buildComponent({ inlineTemplateValue: 'existing template text' });
    expect(screen.getByRole('textbox')).toHaveValue('existing template text');
    expect(editInlineTemplate).not.toHaveBeenCalled();
  });

  it('dispatches the edited value via editInlineTemplate when the user types', () => {
    const { editInlineTemplate } = buildComponent({ inlineTemplateValue: '' });
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'my custom query' } });
    expect(editInlineTemplate).toHaveBeenCalledWith('my custom query');
  });

  it('shows a "Template is required" error when there is no example and no value', () => {
    buildComponent({ inlineTemplateValue: '', providerVariableHints: undefined });
    expect(screen.getByText(/Template is required/)).toBeVisible();
  });

  it('does not render a "Saved Templates" dropdown', () => {
    buildComponent();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(screen.queryByText(/Saved Templates/)).not.toBeInTheDocument();
  });

  it('does not render a "Save as reusable template" button', () => {
    buildComponent();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByText(/Save as reusable template/)).not.toBeInTheDocument();
  });

  it('renders the provider variable hint line', () => {
    buildComponent();
    expect(screen.getByText(/Available variables: \$\{scope\}, \$\{location\}/)).toBeVisible();
  });
});
