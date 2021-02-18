import 'brace/mode/yaml';
import 'brace/theme/textmate';
import { loadAll, YAMLException } from 'js-yaml';
import { $log } from 'ngimport';
import React from 'react';
import AceEditor, { Annotation } from 'react-ace';

import { yamlStringToDocuments } from './yamlEditorUtils';

export interface IYamlEditorProps {
  value: string;
  onChange(raw: string, obj: any): void;
}

// js-yaml's typing for YAMLException doesn't include a type
// for `mark`.
interface IMark {
  buffer: string;
  column: number;
  line: number;
  name: string;
  position: string;
}

export class YamlEditor extends React.Component<IYamlEditorProps> {
  private handleChange = (raw: string) => {
    const yamlDocuments = yamlStringToDocuments(raw);
    this.props.onChange
      ? this.props.onChange(raw, yamlDocuments)
      : $log.warn('No `onChange` handler provided for YAML editor.');
  };

  public calculateErrors = (value: string): Annotation[] => {
    try {
      loadAll(value, null);
    } catch (e) {
      if (e instanceof YAMLException) {
        const mark = (e as any).mark as IMark;
        // Ace Editor doesn't render errors for YAML, so
        // we have to do it ourselves.
        return [
          {
            column: mark.column,
            row: mark.line,
            type: 'error',
            text: e.message,
          },
        ];
      }
    }
    return [];
  };

  public render = () => {
    const { value } = this.props;
    return (
      <AceEditor
        mode="yaml"
        theme="textmate"
        name="yaml-editor"
        style={{ width: 'auto' }}
        onChange={this.handleChange}
        fontSize={12}
        showGutter={true}
        cursorStart={0}
        showPrintMargin={false}
        minLines={50}
        maxLines={100}
        highlightActiveLine={true}
        value={value || undefined}
        annotations={this.calculateErrors(value)}
        setOptions={{
          firstLineNumber: 1,
          tabSize: 2,
          showLineNumbers: false,
          showFoldWidgets: false,
        }}
        editorProps={{ $blockScrolling: Infinity }}
        className="ace-editor"
      />
    );
  };
}
