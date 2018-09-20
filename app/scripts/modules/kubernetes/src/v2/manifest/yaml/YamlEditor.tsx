import * as React from 'react';
import AceEditor, { Annotation } from 'react-ace';
import { $log } from 'ngimport';
import { load, dump, YAMLException } from 'js-yaml';

import 'brace/theme/textmate';
import 'brace/mode/yaml';
import './yamlEditor.less';

export interface IYamlEditorProps {
  value: any;
  onChange(value: any): void;
}

export interface IYamlEditorState {
  errors: Annotation[];
  value: string;
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

export class YamlEditor extends React.Component<IYamlEditorProps, IYamlEditorState> {
  public static getDerivedStateFromProps = (props: IYamlEditorProps): IYamlEditorState => {
    return {
      value: props.value ? dump(props.value) : '',
      errors: [],
    };
  };

  constructor(props: IYamlEditorProps) {
    super(props);
    this.state = {
      value: props.value ? dump(props.value) : '',
      errors: [],
    };
  }

  private handleChange = (value: string) => {
    this.setState({ value });
    try {
      this.props.onChange
        ? this.props.onChange(load(value))
        : $log.warn('No `onChange` handler provided for YAML editor.');

      this.setState({
        errors: [],
      });
    } catch (e) {
      if (e instanceof YAMLException) {
        $log.warn(`Error loading YAML from string ${value}: `, e);
        const mark = (e as any).mark as IMark;
        // Ace Editor doesn't render errors for YAML, so
        // we have to do it ourselves.
        this.setState({
          errors: [
            {
              column: mark.column,
              row: mark.line,
              type: 'error',
              text: e.message,
            },
          ],
        });
      } else {
        $log.warn(`Error loading YAML from string ${value}: `, e);
      }
    }
  };

  public render = () => {
    return (
      <AceEditor
        mode="yaml"
        theme="textmate"
        name="yaml-editor"
        style={{ width: 'inherit' }}
        onChange={this.handleChange}
        fontSize={12}
        showGutter={true}
        cursorStart={0}
        showPrintMargin={false}
        minLines={50}
        maxLines={100}
        highlightActiveLine={true}
        value={this.state.value}
        annotations={this.state.errors}
        setOptions={{
          firstLineNumber: 1,
          tabSize: 2,
          showLineNumbers: false,
          showFoldWidgets: false,
        }}
      />
    );
  };
}
