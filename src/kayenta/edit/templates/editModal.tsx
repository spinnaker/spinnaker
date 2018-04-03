import * as React from 'react';
import { Modal } from 'react-bootstrap';
import { connect } from 'react-redux';
import { noop } from '@spinnaker/core';
import { ICanaryState } from 'kayenta/reducers';
import { editingTemplateSelector } from 'kayenta/selectors';
import { Dispatch } from 'redux';
import FormRow from 'kayenta/layout/formRow';
import { DisableableInput, DisableableTextarea, DISABLE_EDIT_CONFIG } from 'kayenta/layout/disableable';
import * as Creators from 'kayenta/actions/creators';
import Styleguide from 'kayenta/layout/styleguide';

interface IEditTemplateModalStateProps {
  name: string;
  value: string;
}

interface IEditTemplateModalDispatchProps {
  editName: (event: any) => void;
  editValue: (event: any) => void;
  cancel: () => void;
  confirm: () => void;
}

const EditTemplateModal = ({ name, editName, value, editValue, cancel, confirm }: IEditTemplateModalStateProps & IEditTemplateModalDispatchProps) => {
  return (
    <Modal onHide={noop} show={name !== null && value !== null}>
      <Styleguide>
        <Modal.Header>
          <Modal.Title>Edit Template</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <FormRow label="Name">
            <DisableableInput
              type="text"
              value={name || ''}
              onChange={editName}
              disabledStateKeys={[DISABLE_EDIT_CONFIG]}
            />
          </FormRow>
          <FormRow label="Template">
            <DisableableTextarea
              value={value || ''}
              onChange={editValue}
              disabledStateKeys={[DISABLE_EDIT_CONFIG]}
            />
          </FormRow>
        </Modal.Body>
        <Modal.Footer>
          <ul className="list-inline pull-right">
            <li><button className="passive" onClick={cancel}>Cancel</button></li>
            <li><button className="primary" onClick={confirm} disabled={!name}>OK</button></li>
          </ul>
        </Modal.Footer>
      </Styleguide>
    </Modal>
  );
};

const mapStateToProps = (state: ICanaryState) => ({
  name: editingTemplateSelector(state)
    ? editingTemplateSelector(state).editedName
    : null,
  value: editingTemplateSelector(state)
    ? editingTemplateSelector(state).editedValue
    : null,
});

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>) => ({
  editName: (event: any) =>
    dispatch(Creators.editTemplateName({ name: event.target.value })),
  editValue: (event: any) =>
    dispatch(Creators.editTemplateValue({ value: event.target.value })),
  cancel: () =>
    dispatch(Creators.editTemplateCancel()),
  confirm: () =>
    dispatch(Creators.editTemplateConfirm()),
});

export default connect(mapStateToProps, mapDispatchToProps)(EditTemplateModal);
