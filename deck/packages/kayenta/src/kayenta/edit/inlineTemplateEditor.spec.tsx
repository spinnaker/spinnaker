import { render, screen } from '@testing-library/react';
import { identity } from 'lodash';
import * as React from 'react';
import { Provider } from 'react-redux';
import { createStore } from 'redux';

import { noop } from '@spinnaker/core';

import { IInlineTemplateEditorProps, InlineTemplateEditor } from './inlineTemplateEditor';

describe('<InlineTemplateEditor />', () => {
  const buildComponent = (props: IInlineTemplateEditorProps) =>
    render(
      <Provider store={createStore(() => ({}))}>
        <InlineTemplateEditor {...props} />
      </Provider>,
    );

  it('renders a textarea with template value', () => {
    buildComponent({
      templateValue: 'metadata.user_labels."app"="${scope}"',
      transformValueForSave: identity,
      editTemplateValue: noop,
    });
    expect(screen.getByRole('textbox')).toHaveValue('metadata.user_labels."app"="${scope}"');
  });
  it('renders an error for empty input', () => {
    buildComponent({
      templateValue: 'metadata.user_labels."app"="${scope}"',
      transformValueForSave: identity,
      editTemplateValue: noop,
    });
    expect(screen.queryByText('Template is required')).not.toBeInTheDocument();

    buildComponent({
      templateValue: '',
      transformValueForSave: identity,
      editTemplateValue: noop,
    });
    expect(screen.getByText('Template is required')).toBeVisible();
  });
});
