import * as Creators from 'kayenta/actions/creators';
import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryFilterTemplateValidationMessages } from 'kayenta/edit/filterTemplatesValidation';
import {
  DISABLE_EDIT_CONFIG,
  DisableableInput,
  DisableableReactSelect,
  DisableableTextarea,
} from 'kayenta/layout/disableable';
import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import { configTemplatesSelector, editingTemplateSelector } from 'kayenta/selectors';
import {
  editingTemplateValidationSelector,
  selectedTemplateNameSelector,
} from 'kayenta/selectors/filterTemplatesSelectors';
import { get, isString } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';
import { Option } from 'react-select';
import { Dispatch } from 'redux';

import './filterTemplateSelector.less';

interface IFilterTemplateSelectorStateProps {
  editedTemplateName: string;
  editedTemplateValue: string;
  selectedTemplateName: string;
  templates: { [key: string]: string };
  validation: ICanaryFilterTemplateValidationMessages;
}

interface IFilterTemplateSelectorDispatchProps {
  deleteTemplate: (e: any, name: string) => void;
  editTemplateBegin: (e: any, name: string, value: string) => void;
  editTemplateCancel: () => void;
  editTemplateConfirm: () => void;
  editTemplateName: (event: any) => void;
  editTemplateValue: (event: any) => void;
  selectTemplate: (option: Option) => void;
}

export type IFilterTemplateSelectorProps = IFilterTemplateSelectorStateProps & IFilterTemplateSelectorDispatchProps;

export class FilterTemplateSelector extends React.Component<IFilterTemplateSelectorProps> {
  private getOptions = (): Option[] => {
    const templateOptions = Object.keys(this.props.templates).map((t) => ({
      value: t,
      label: t,
      requestingNew: false,
    }));
    templateOptions.push({ value: null, label: null, requestingNew: true });
    return templateOptions;
  };

  private optionRenderer = (option: Option<string>): JSX.Element => {
    if (option.requestingNew) {
      return <span>Create new...</span>;
    }
    return (
      <span className="filter-template-option">
        <span>{option.label}</span>
        <span>
          <button className="link" onMouseDown={(e) => this.props.deleteTemplate(e, option.value)}>
            Delete
          </button>
          <button
            className="link"
            onMouseDown={(e) => this.props.editTemplateBegin(e, option.value, this.props.templates[option.value])}
          >
            Edit
          </button>
        </span>
      </span>
    );
  };

  public render() {
    const {
      editedTemplateName,
      editedTemplateValue,
      editTemplateCancel,
      editTemplateConfirm,
      editTemplateName,
      editTemplateValue,
      selectTemplate,
      selectedTemplateName,
      templates,
      validation,
    } = this.props;
    const isEditing = isString(editedTemplateName) && isString(editedTemplateValue);
    return (
      <>
        <FormRow label="Filter Template" helpId="canary.config.filterTemplate" inputOnly={true}>
          {!isEditing && (
            <DisableableReactSelect
              value={selectedTemplateName}
              disabledStateKeys={[DISABLE_EDIT_CONFIG]}
              disabled={CanarySettings.disableConfigEdit}
              onChange={selectTemplate}
              options={this.getOptions()}
              optionRenderer={this.optionRenderer}
            />
          )}
        </FormRow>
        {isEditing && (
          <>
            <FormRow label="Name" error={get(validation, 'errors.templateName.message')} inputOnly={true}>
              <DisableableInput
                disabledStateKeys={[DISABLE_EDIT_CONFIG]}
                disabled={CanarySettings.disableConfigEdit}
                onChange={editTemplateName}
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
                onChange={editTemplateValue}
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
                  onClick={editTemplateConfirm}
                >
                  Save
                </button>
              </div>
            </FormRow>
          </>
        )}
        {!isEditing && selectedTemplateName && (
          <FormRow>
            <pre className="template-editor-value-formatted">{templates[selectedTemplateName]}</pre>
          </FormRow>
        )}
      </>
    );
  }
}

const mapStateToProps = (state: ICanaryState): IFilterTemplateSelectorStateProps => ({
  editedTemplateName: get(editingTemplateSelector(state), 'editedName'),
  editedTemplateValue: get(editingTemplateSelector(state), 'editedValue'),
  selectedTemplateName: selectedTemplateNameSelector(state),
  templates: configTemplatesSelector(state),
  validation: editingTemplateValidationSelector(state),
});

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>): IFilterTemplateSelectorDispatchProps => ({
  deleteTemplate: (e: any, name: string) => {
    e.stopPropagation();
    dispatch(Creators.selectTemplate({ name: null }));
    dispatch(Creators.deleteTemplate({ name }));
  },
  editTemplateBegin: (e: any, name: string, value: string) => {
    e.stopPropagation();
    dispatch(Creators.selectTemplate({ name }));
    dispatch(
      Creators.editTemplateBegin({
        name,
        value,
      }),
    );
  },
  editTemplateCancel: () => dispatch(Creators.editTemplateCancel()),
  editTemplateConfirm: () => dispatch(Creators.editTemplateConfirm()),
  editTemplateName: (event: any) => dispatch(Creators.editTemplateName({ name: event.target.value })),
  editTemplateValue: (event: any) => dispatch(Creators.editTemplateValue({ value: event.target.value })),
  selectTemplate: (option: Option<string>) => {
    if (option && option.requestingNew === true) {
      dispatch(Creators.selectTemplate({ name: null }));
      dispatch(Creators.addTemplate());
    } else {
      dispatch(Creators.selectTemplate({ name: option ? option.value : null }));
    }
  },
});

export default connect(mapStateToProps, mapDispatchToProps)(FilterTemplateSelector);
