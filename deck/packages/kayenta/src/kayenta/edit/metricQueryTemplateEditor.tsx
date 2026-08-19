import { isEmpty } from 'lodash';
import * as React from 'react';
import { useState } from 'react';
import { connect } from 'react-redux';
import type { Dispatch } from 'redux';

import * as Creators from '../actions/creators';
import { CanarySettings } from '../canary.settings';
import { DISABLE_EDIT_CONFIG, DisableableTextarea } from '../layout/disableable';
import FormRow from '../layout/formRow';
import type { ICanaryState } from '../reducers';
import { inlineTemplateValueSelector, transformInlineTemplateForSave } from '../selectors/filterTemplatesSelectors';
import type { ITemplateProviderVariables } from './templateProviderVariables';

import './metricQueryTemplateEditor.less';

export interface IMetricQueryTemplateEditorOwnProps {
  // Per-provider "available variables" hint (see templateProviderVariables.ts), keyed by the
  // metric's query.serviceType/query.type.
  providerVariableHints?: ITemplateProviderVariables;
}

interface IMetricQueryTemplateEditorStateProps {
  inlineTemplateValue: string;
  transformValueForSave: (value: string) => string;
}

interface IMetricQueryTemplateEditorDispatchProps {
  editInlineTemplate: (value: string) => void;
}

export type IMetricQueryTemplateEditorProps = IMetricQueryTemplateEditorOwnProps &
  IMetricQueryTemplateEditorStateProps &
  IMetricQueryTemplateEditorDispatchProps;

/*
 * Query template editor: a single always-visible textarea bound to query.template. There is no
 * separate "saved/named template" concept any more -- every metric just has its own template
 * text.
 *
 * When the textarea would otherwise render empty (a new metric, or an existing one with no
 * template yet), it's pre-filled with the provider's illustrative example query so the user has
 * real starter text to edit instead of a blank box. That pre-fill is purely local/display-only
 * until the user actually types into the field -- it is never dispatched to redux on its own, so
 * an untouched metric never silently ends up with the example text saved as its real template.
 */
export function MetricQueryTemplateEditor({
  providerVariableHints,
  inlineTemplateValue,
  transformValueForSave,
  editInlineTemplate,
}: IMetricQueryTemplateEditorProps) {
  // Becomes true the first time the user edits the textarea. Until then, an empty redux value is
  // shown with the provider's example text as local-only starter content.
  const [touched, setTouched] = useState(false);

  const exampleText = (providerVariableHints && providerVariableHints.example) || '';
  const displayValue = touched || !isEmpty(inlineTemplateValue) ? inlineTemplateValue || '' : exampleText;

  const handleTextareaChange = (e: any) => {
    if (!touched) {
      setTouched(true);
    }
    editInlineTemplate(transformValueForSave(e.target.value));
  };

  return (
    <FormRow
      label="Query Template"
      helpId="canary.config.filterTemplate"
      inputOnly={true}
      error={isEmpty(displayValue) && 'Template is required'}
    >
      {providerVariableHints && (
        <div className="body-small color-text-caption template-variable-hint" style={{ marginBottom: '5px' }}>
          Available variables: {providerVariableHints.variables.map((v) => `\${${v}}`).join(', ')}
          {providerVariableHints.note && <> — {providerVariableHints.note}</>}
        </div>
      )}
      <DisableableTextarea
        className="template-editor-textarea"
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        disabled={CanarySettings.disableConfigEdit}
        onChange={handleTextareaChange}
        value={displayValue}
      />
    </FormRow>
  );
}

const mapStateToProps = (state: ICanaryState): IMetricQueryTemplateEditorStateProps => ({
  inlineTemplateValue: inlineTemplateValueSelector(state),
  transformValueForSave: transformInlineTemplateForSave(state),
});

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>): IMetricQueryTemplateEditorDispatchProps => ({
  editInlineTemplate: (value: string) => dispatch(Creators.editInlineTemplate({ value })),
});

export default connect(mapStateToProps, mapDispatchToProps)(MetricQueryTemplateEditor);
