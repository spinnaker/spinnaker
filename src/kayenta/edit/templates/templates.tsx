import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryState } from 'kayenta/reducers';
import { Table, ITableColumn } from 'kayenta/layout/table';
import { configTemplatesSelector } from 'kayenta/selectors';

interface ITemplatesStateProps {
  templates: ITemplate[];
}

interface ITemplate {
  name: string;
  value: string;
}

const Templates = ({ templates }: ITemplatesStateProps) => {
  const columns: ITableColumn<ITemplate>[] = [
    {
      label: 'Template Name',
      width: 1,
      getContent: t => <span>{t.name}</span>,
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

export default connect(mapStateToProps)(Templates);
