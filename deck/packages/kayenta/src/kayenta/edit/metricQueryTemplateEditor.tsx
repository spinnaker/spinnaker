import { get, isEmpty, isString } from 'lodash';
import * as React from 'react';
import { useState } from 'react';
import { connect } from 'react-redux';
import type { Option } from 'react-select';
import type { Dispatch } from 'redux';

import * as Creators from '../actions/creators';
import { CanarySettings } from '../canary.settings';
import type { ICanaryFilterTemplateValidationMessages } from './filterTemplatesValidation';
import {
  DISABLE_EDIT_CONFIG,
  DisableableInput,
  DisableableReactSelect,
  DisableableTextarea,
} from '../layout/disableable';
import FormRow from '../layout/formRow';
import type { ICanaryState } from '../reducers';
import { configTemplatesSelector, editingTemplateSelector } from '../selectors';
import {
  editingTemplateValidationSelector,
  inlineTemplateValueSelector,
  selectedTemplateNameSelector,
  transformInlineTemplateForSave,
} from '../selectors/filterTemplatesSelectors';
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
  selectedTemplateName: string;
  templates: { [key: string]: string };
  editedTemplateName: string;
  editedTemplateValue: string;
  validation: ICanaryFilterTemplateValidationMessages;
}

interface IMetricQueryTemplateEditorDispatchProps {
  editInlineTemplate: (value: string) => void;
  selectTemplate: (name: string) => void;
  addTemplate: () => void;
  deleteTemplate: (name: string) => void;
  editTemplateBegin: (name: string, value: string) => void;
  editTemplateCancel: () => void;
  editTemplateConfirm: () => void;
  editTemplateName: (name: string) => void;
  editTemplateValue: (value: string) => void;
}

export type IMetricQueryTemplateEditorProps = IMetricQueryTemplateEditorOwnProps &
  IMetricQueryTemplateEditorStateProps &
  IMetricQueryTemplateEditorDispatchProps;

/*
 * Unified query template editor. Replaces the old separate FilterTemplateSelector (named/saved
 * templates only) and InlineTemplateEditor (raw text only, Prometheus PromQL-only) components.
 *
 * A single always-visible textarea is bound to query.customInlineTemplate. A "Saved Templates"
 * panel below it lets the user load a named template from the config's `templates` map (which
 * populates the textarea with that template's resolved text for editing, and points
 * query.customFilterTemplate at it) or save the current textarea content as a new/updated named
 * template. Inline and named-template state are kept mutually exclusive: editing the textarea
 * after loading a named template detaches back to inline-only editing.
 */
export function MetricQueryTemplateEditor({
  providerVariableHints,
  inlineTemplateValue,
  transformValueForSave,
  selectedTemplateName,
  templates,
  editedTemplateName,
  editedTemplateValue,
  validation,
  editInlineTemplate,
  selectTemplate,
  addTemplate,
  deleteTemplate,
  editTemplateBegin,
  editTemplateCancel,
  editTemplateConfirm,
  editTemplateName,
  editTemplateValue,
}: IMetricQueryTemplateEditorProps) {
  // Remembers which saved template the textarea most recently diverged from, purely to power the
  // "detached from '<name>'" note -- the redux-level link (query.customFilterTemplate) is cleared
  // as soon as divergence happens.
  const [detachedFrom, setDetachedFrom] = useState<string>(null);

  const isEditingSavedTemplate = isString(editedTemplateName) && isString(editedTemplateValue);

  // What the main textarea shows: the raw inline template if the user has typed anything,
  // otherwise the resolved content of the currently-loaded saved template (if any).
  const displayValue = !isEmpty(inlineTemplateValue)
    ? inlineTemplateValue
    : selectedTemplateName && templates && templates[selectedTemplateName] != null
    ? templates[selectedTemplateName]
    : inlineTemplateValue || '';

  const handleTextareaChange = (e: any) => {
    const value = transformValueForSave(e.target.value);
    if (selectedTemplateName && isEmpty(inlineTemplateValue)) {
      // Textarea was showing a loaded-but-unedited named template; this keystroke detaches it.
      setDetachedFrom(selectedTemplateName);
      selectTemplate(null);
    }
    editInlineTemplate(value);
  };

  const handleLoadTemplate = (option: Option<string>) => {
    if (option && (option as any).requestingNew) {
      selectTemplate(null);
      addTemplate();
      return;
    }
    setDetachedFrom(null);
    selectTemplate(option ? option.value : null);
    // Inline always wins over named server-side, so clear it -- the resolved template text will
    // show through `templates[selectedTemplateName]` above.
    editInlineTemplate('');
  };

  const getOptions = (): Option[] => {
    const templateOptions = Object.keys(templates || {}).map((t) => ({ value: t, label: t, requestingNew: false }));
    templateOptions.push({ value: null, label: null, requestingNew: true });
    return templateOptions;
  };

  const optionRenderer = (option: Option<string>): JSX.Element => {
    if ((option as any).requestingNew) {
      return <span>Create new...</span>;
    }
    return (
      <span className="filter-template-option">
        <span>{option.label}</span>
        <span>
          <button
            className="link"
            onMouseDown={(e) => {
              e.stopPropagation();
              selectTemplate(null);
              deleteTemplate(option.value);
            }}
          >
            Delete
          </button>
          <button
            className="link"
            onMouseDown={(e) => {
              e.stopPropagation();
              setDetachedFrom(null);
              selectTemplate(option.value);
              editTemplateBegin(option.value, templates[option.value]);
            }}
          >
            Edit
          </button>
        </span>
      </span>
    );
  };

  // Opens the name/value form (reused from the old FilterTemplateSelector) pre-populated with the
  // main textarea's current content, so the user doesn't have to retype it.
  const beginSaveAsTemplate = () => {
    if (selectedTemplateName) {
      editTemplateBegin(selectedTemplateName, displayValue);
    } else {
      addTemplate();
      editTemplateValue(displayValue);
      if (detachedFrom) {
        editTemplateName(detachedFrom);
      }
    }
  };

  const confirmSaveAsTemplate = () => {
    const nameToSelect = editedTemplateName;
    editTemplateConfirm();
    // Point this metric at the just-saved template and clear the inline value, keeping the two
    // mutually exclusive regardless of how editTemplateConfirm's own bookkeeping resolves it.
    selectTemplate(nameToSelect);
    editInlineTemplate('');
    setDetachedFrom(null);
  };

  const showDetachedNote = Boolean(detachedFrom) && !selectedTemplateName && !isEmpty(inlineTemplateValue);
  const templatesEnabled = CanarySettings.templatesEnabled !== false;

  return (
    <>
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
        {showDetachedNote && (
          <div className="body-small color-text-caption template-detached-note" style={{ marginTop: '5px' }}>
            Editing detaches from saved template '{detachedFrom}' — save again to update it, or give it a new name to
            save separately.
          </div>
        )}
        <div className="horizontal" style={{ marginTop: '5px' }}>
          <button className="link" onClick={beginSaveAsTemplate} disabled={CanarySettings.disableConfigEdit}>
            {selectedTemplateName ? `Save changes to '${selectedTemplateName}'...` : 'Save as reusable template...'}
          </button>
        </div>
      </FormRow>
      {templatesEnabled && !isEditingSavedTemplate && (
        <FormRow label="Saved Templates" inputOnly={true}>
          <DisableableReactSelect
            value={selectedTemplateName}
            disabledStateKeys={[DISABLE_EDIT_CONFIG]}
            disabled={CanarySettings.disableConfigEdit}
            onChange={handleLoadTemplate}
            options={getOptions()}
            optionRenderer={optionRenderer}
            placeholder="Load a saved template..."
          />
        </FormRow>
      )}
      {isEditingSavedTemplate && (
        <>
          <FormRow label="Name" error={get(validation, 'errors.templateName.message')} inputOnly={true}>
            <DisableableInput
              disabledStateKeys={[DISABLE_EDIT_CONFIG]}
              disabled={CanarySettings.disableConfigEdit}
              onChange={(e: any) => editTemplateName(e.target.value)}
              value={editedTemplateName}
            />
          </FormRow>
          <FormRow
            label="Template"
            inputOnly={true}
            error={get(validation, 'errors.templateValue.message')}
            warning={get(validation, 'warnings.template.message')}
          >
            <DisableableTextarea
              className="template-editor-textarea"
              disabledStateKeys={[DISABLE_EDIT_CONFIG]}
              disabled={CanarySettings.disableConfigEdit}
              onChange={(e: any) => editTemplateValue(e.target.value)}
              value={editedTemplateValue}
            />
          </FormRow>
          <FormRow>
            <div className="horizontal pull-right">
              <button className="link" onClick={editTemplateCancel}>
                Cancel
              </button>
              <button
                className="link"
                disabled={[validation.errors.templateName, validation.errors.templateValue].some((e) => e != null)}
                onClick={confirmSaveAsTemplate}
              >
                Save
              </button>
            </div>
          </FormRow>
        </>
      )}
      {!isEditingSavedTemplate && selectedTemplateName && templates && templates[selectedTemplateName] && (
        <FormRow label="Resolved Preview">
          <pre className="template-editor-value-formatted">{templates[selectedTemplateName]}</pre>
        </FormRow>
      )}
    </>
  );
}

const mapStateToProps = (state: ICanaryState): IMetricQueryTemplateEditorStateProps => ({
  inlineTemplateValue: inlineTemplateValueSelector(state),
  transformValueForSave: transformInlineTemplateForSave(state),
  selectedTemplateName: selectedTemplateNameSelector(state),
  templates: configTemplatesSelector(state),
  editedTemplateName: get(editingTemplateSelector(state), 'editedName'),
  editedTemplateValue: get(editingTemplateSelector(state), 'editedValue'),
  validation: editingTemplateValidationSelector(state),
});

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>): IMetricQueryTemplateEditorDispatchProps => ({
  editInlineTemplate: (value: string) => dispatch(Creators.editInlineTemplate({ value })),
  selectTemplate: (name: string) => dispatch(Creators.selectTemplate({ name })),
  addTemplate: () => dispatch(Creators.addTemplate()),
  deleteTemplate: (name: string) => dispatch(Creators.deleteTemplate({ name })),
  editTemplateBegin: (name: string, value: string) => dispatch(Creators.editTemplateBegin({ name, value })),
  editTemplateCancel: () => dispatch(Creators.editTemplateCancel()),
  editTemplateConfirm: () => dispatch(Creators.editTemplateConfirm()),
  editTemplateName: (name: string) => dispatch(Creators.editTemplateName({ name })),
  editTemplateValue: (value: string) => dispatch(Creators.editTemplateValue({ value })),
});

export default connect(mapStateToProps, mapDispatchToProps)(MetricQueryTemplateEditor);
