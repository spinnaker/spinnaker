import * as React from 'react';
import * as Select from 'react-select';
import {IPipelineTemplate} from 'core/pipeline/config/templates/pipelineTemplate.service';
import autoBindMethods from 'class-autobind-decorator';

interface IProps {
  selectedTemplate: IPipelineTemplate;
  templates: IPipelineTemplate[];
  onChange: (template: IPipelineTemplate) => void;
}

interface IState { }

@autoBindMethods
export class ManagedTemplateSelector extends React.Component<IProps, IState> {

  public render() {
    const selected = this.props.selectedTemplate;
    return (
      <div className="form-group clearfix">
        <div className="col-md-7 col-md-offset-3">
          <Select
            options={this.createOptionsFromTemplates()}
            clearable={true}
            value={selected ? {label: selected.metadata.name, value: selected.id} : null}
            onChange={this.handleTemplateSelect}
            optionRenderer={this.templateOptionRenderer}
          />
        </div>
      </div>
    );
  }

  private createOptionsFromTemplates(): Select.Option[] {
    return this.props.templates.map(t => ({label: t.metadata.name, value: t.id}));
  }

  private handleTemplateSelect(option: Select.Option): void {
    let selectedTemplate: IPipelineTemplate;
    if (option) {
      selectedTemplate = this.props.templates.find(t => t.id === option.value);
    }
    this.props.onChange(selectedTemplate);
  }

  private templateOptionRenderer(option: Select.Option) {
    const template = this.props.templates.find(t => t.id === option.value);
    return (
      <div>
        <h5 style={{marginBottom: '0'}}>{template.metadata.name}</h5>
        {template.metadata.owner && (<div><span className="small">{template.metadata.owner}</span><br/></div>)}
        {template.metadata.description && (<span className="small">{template.metadata.description}</span>)}
      </div>
    );
  }
}
