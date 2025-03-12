import * as Creators from 'kayenta/actions/creators';
import { CanarySettings } from 'kayenta/canary.settings';
import { DISABLE_EDIT_CONFIG, DisableableTextarea } from 'kayenta/layout/disableable';
import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import {
  inlineTemplateValueSelector,
  transformInlineTemplateForSave,
} from 'kayenta/selectors/filterTemplatesSelectors';
import { isEmpty } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';

interface IInlineTemplateEditorStateProps {
  templateValue: string;
  transformValueForSave: (value: string) => string;
}

interface IInlineTemplateEditorDispatchProps {
  editTemplateValue: (value: string) => void;
}

export type IInlineTemplateEditorProps = IInlineTemplateEditorStateProps & IInlineTemplateEditorDispatchProps;

export function InlineTemplateEditor({
  editTemplateValue,
  templateValue,
  transformValueForSave,
}: IInlineTemplateEditorProps) {
  return (
    <FormRow
      label="Template"
      inputOnly={true}
      helpId="canary.config.filterTemplate"
      error={isEmpty(templateValue) && 'Template is required'}
    >
      <DisableableTextarea
        className="template-editor-textarea"
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        disabled={CanarySettings.disableConfigEdit}
        onChange={(e: any) => editTemplateValue(transformValueForSave(e.target.value))}
        value={templateValue}
      />
    </FormRow>
  );
}

function mapStateToProps(state: ICanaryState): IInlineTemplateEditorStateProps {
  return {
    templateValue: inlineTemplateValueSelector(state),
    transformValueForSave: transformInlineTemplateForSave(state),
  };
}

function mapDispatchToProps(dispatch: any): IInlineTemplateEditorDispatchProps {
  return {
    editTemplateValue: (value: string) => dispatch(Creators.editInlineTemplate({ value })),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(InlineTemplateEditor);
