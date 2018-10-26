import * as React from 'react';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';

import { ICanaryState } from 'kayenta/reducers';
import { ITableColumn, NativeTable } from 'kayenta/layout/table';
import { configTemplatesSelector } from 'kayenta/selectors';
import * as Creators from 'kayenta/actions/creators';
import EditTemplateModal from './editModal';
import { DisableableButton, DISABLE_EDIT_CONFIG } from 'kayenta/layout/disableable';

import './templates.less';

interface ITemplatesStateProps {
  templates: ITemplate[];
}

interface ITemplatesDispatchProps {
  edit: (event: any) => void;
  remove: (event: any) => void;
  add: () => void;
}

export interface ITemplate {
  name: string;
  value: string;
}

const Templates = ({ templates, edit, remove, add }: ITemplatesStateProps & ITemplatesDispatchProps) => {
  const columns: ITableColumn<ITemplate>[] = [
    {
      label: 'Template Name',
      getContent: t => <span>{t.name}</span>,
    },
    {
      getContent: t => (
        <div className="horizontal pull-right templates-action-buttons">
          <button className="link" data-name={t.name} data-value={t.value} onClick={edit} style={{ marginTop: '1px' }}>
            Edit
          </button>
          <button className="link" data-name={t.name} onClick={remove}>
            Delete
          </button>
        </div>
      ),
    },
  ];

  return (
    <>
      <EditTemplateModal />
      <NativeTable rows={templates} columns={columns} rowKey={t => t.name} className="header-transparent" />
      <DisableableButton className="passive self-left" onClick={add} disabledStateKeys={[DISABLE_EDIT_CONFIG]}>
        Add Template
      </DisableableButton>
    </>
  );
};

const mapStateToProps = (state: ICanaryState) => {
  const templates = configTemplatesSelector(state) || {};
  return {
    templates: Object.keys(templates).map(key => ({
      name: key,
      value: templates[key],
    })),
  };
};

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>) => ({
  edit: (event: any) =>
    dispatch(
      Creators.editTemplateBegin({
        name: event.target.dataset.name,
        value: event.target.dataset.value,
      }),
    ),
  remove: (event: any) =>
    dispatch(
      Creators.deleteTemplate({
        name: event.target.dataset.name,
      }),
    ),
  add: () =>
    dispatch(
      Creators.editTemplateBegin({
        name: '',
        value: '',
      }),
    ),
});

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(Templates);
