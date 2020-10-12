import React from 'react';
import Select, { Option } from 'react-select';
import { IPipelineTemplate } from '../config/templates/PipelineTemplateReader';

export interface IManagedTemplateSelectorProps {
  selectedTemplate: IPipelineTemplate;
  templates: IPipelineTemplate[];
  onChange: (template: IPipelineTemplate) => void;
}

export class ManagedTemplateSelector extends React.Component<IManagedTemplateSelectorProps> {
  public render() {
    const selected = this.props.selectedTemplate;
    return (
      <div className="form-group clearfix">
        <div className="col-md-7 col-md-offset-3">
          <Select
            options={this.createOptionsFromTemplates()}
            clearable={true}
            value={selected ? { label: selected.metadata.name, value: selected.id } : null}
            onChange={this.handleTemplateSelect}
            optionRenderer={this.templateOptionRenderer}
            onSelectResetsInput={false}
          />
        </div>
      </div>
    );
  }

  private createOptionsFromTemplates(): Option[] {
    return this.props.templates.map((t) => ({ label: t.metadata.name, value: t.id }));
  }

  private handleTemplateSelect = (option: Option): void => {
    let selectedTemplate: IPipelineTemplate;
    if (option) {
      selectedTemplate = this.props.templates.find((t) => t.id === option.value);
    }
    this.props.onChange(selectedTemplate);
  };

  private templateOptionRenderer = (option: Option) => {
    const template = this.props.templates.find((t) => t.id === option.value);
    return (
      <div>
        <h5 style={{ marginBottom: '0' }}>{template.metadata.name}</h5>
        {template.selfLink && <p className="small">{template.selfLink}</p>}
        {template.metadata.owner && <p className="small">{template.metadata.owner}</p>}
        {template.metadata.description && <p className="small">{template.metadata.description}</p>}
      </div>
    );
  };
}
