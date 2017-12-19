import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryState } from 'kayenta/reducers';
import { Table, ITableColumn } from 'kayenta/layout/table';
import { configTemplatesSelector } from 'kayenta/selectors';
import { Dispatch } from 'redux';
import * as Creators from 'kayenta/actions/creators';
import EditTemplateModal from './editModal';

interface ITemplatesStateProps {
  templates: ITemplate[];
}

interface ITemplatesDispatchProps {
  edit: (event: any) => void;
}

interface ITemplate {
  name: string;
  value: string;
}

const Templates = ({ templates, edit }: ITemplatesStateProps & ITemplatesDispatchProps) => {
  const columns: ITableColumn<ITemplate>[] = [
    {
      label: 'Template Name',
      width: 9,
      getContent: t => <span>{t.name}</span>,
    },
    {
      width: 1,
      getContent: t => (
        <div className="horizontal center">
          <i
            className="fa fa-edit"
            data-name={t.name}
            data-value={t.value}
            onClick={edit}
          />
          <EditTemplateModal/>
        </div>
      ),
    }
  ];

  return (
    <Table
      rows={templates}
      columns={columns}
      rowKey={t => t.name}
    />
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
  edit: (event: any) => dispatch(Creators.editTemplateBegin({
    name: event.target.dataset.name,
    value: event.target.dataset.value,
  })),
});

export default connect(mapStateToProps, mapDispatchToProps)(Templates);
