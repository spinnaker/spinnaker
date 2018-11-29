import * as React from 'react';
import { connect } from 'react-redux';
import { isEmpty } from 'lodash';

import FormRow from 'kayenta/layout/formRow';
import { DISABLE_EDIT_CONFIG, DisableableTextarea } from 'kayenta/layout/disableable';
import { ICanaryState } from 'kayenta/reducers';
import * as Creators from 'kayenta/actions/creators';
import {
  inlineTemplateValueSelector,
  transformInlineTemplateForSave,
} from 'kayenta/selectors/filterTemplatesSelectors';

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
      helpId="canary.config.filterTemplate"
      error={isEmpty(templateValue) && 'Template is required'}
    >
      <DisableableTextarea
        className="template-editor-textarea"
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
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

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(InlineTemplateEditor);
