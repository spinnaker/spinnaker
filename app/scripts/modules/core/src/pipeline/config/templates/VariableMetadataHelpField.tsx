import * as React from 'react';

import { NgReact } from 'core/reactShims';
import { IVariableMetadata } from './pipelineTemplate.service';

export interface IVariableMetadataHelpFieldProps {
  metadata: IVariableMetadata
}

export interface IVariableMetadataHelpFieldState { }

export class VariableMetadataHelpField extends React.Component<IVariableMetadataHelpFieldProps, IVariableMetadataHelpFieldState> {

   public render() {
     const { HelpField } = NgReact;
     // TODO(dpeach): Clean this up - HelpField is undefined during tests. We need ReactInjector to run, but importing ReactInjector breaks tests.
     return HelpField ? <HelpField content={this.getContent()}/> : null;
   }

   private getContent(): string {
     let content = `<p>${this.props.metadata.description}</p>
                    <p><strong>Type:</strong> <span>${this.props.metadata.type}</span></p>`;

     if (this.props.metadata.example) {
       content += `<p><strong>Example:</strong> <br> <pre class="small">${this.props.metadata.example}</pre></p>`;
     }
     return content;
   }
}
