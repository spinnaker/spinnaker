import { isEmpty } from 'lodash';
import * as React from 'react';
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
 * The provider's illustrative example is shown as the textarea's placeholder, never as its value,
 * so an untouched metric has an empty template and is flagged as required (and blocked from being
 * confirmed) rather than silently saving the example text or an empty query.
 */
export function MetricQueryTemplateEditor({
  providerVariableHints,
  inlineTemplateValue,
  transformValueForSave,
  editInlineTemplate,
}: IMetricQueryTemplateEditorProps) {
  const handleTextareaChange = (e: any) => {
    editInlineTemplate(transformValueForSave(e.target.value));
  };

  const helpId = providerVariableHints
    ? `canary.config.template.${providerVariableHints.serviceType}`
    : 'canary.config.filterTemplate';

  return (
    <FormRow
      label="Query Template"
      helpId={helpId}
      inputOnly={true}
      error={isEmpty(inlineTemplateValue) && 'Template is required'}
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
        placeholder={providerVariableHints?.example}
        value={inlineTemplateValue || ''}
      />
    </FormRow>
  );
}

const mapStateToProps = (state: ICanaryState): IMetricQueryTemplateEditorStateProps => ({
  inlineTemplateValue: inlineTemplateValueSelector(state),
  transformValueForSave: transformInlineTemplateForSave(state),
});

const mapDispatchToProps = (dispatch: Dispatch): IMetricQueryTemplateEditorDispatchProps => ({
  editInlineTemplate: (value: string) => dispatch(Creators.editInlineTemplate({ value })),
});

export default connect(mapStateToProps, mapDispatchToProps)(MetricQueryTemplateEditor);
