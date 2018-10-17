import * as React from 'react';
import AceEditor, { Annotation } from 'react-ace';
import { $log } from 'ngimport';
import { load, YAMLException } from 'js-yaml';

import 'brace/theme/textmate';
import 'brace/mode/yaml';
import '../editor.less';

export interface IYamlEditorProps {
  value: string;
  onChange(raw: string, obj: any): void;
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
  public static getDerivedStateFromProps = (props: IYamlEditorProps, state: IYamlEditorState): IYamlEditorState => {
    return {
      value: props.value,
      errors: Array.isArray(state.errors) ? state.errors : [],
    };
  };

  constructor(props: IYamlEditorProps) {
    super(props);
    this.state = {
      value: props.value,
      errors: [],
    };
  }

  private handleChange = (raw: string) => {
    this.setState({ value: raw });
    try {
      const obj = load(raw);
      this.props.onChange
        ? this.props.onChange(raw, obj)
        : $log.warn('No `onChange` handler provided for YAML editor.');
      this.setState({ errors: [] });
    } catch (e) {
      this.props.onChange
        ? this.props.onChange(raw, null)
        : $log.warn('No `onChange` handler provided for YAML editor.');
      if (e instanceof YAMLException) {
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
      }
      $log.warn(`Error loading YAML from string ${raw}: `, e);
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
        value={this.state.value || undefined}
        annotations={this.state.errors}
        setOptions={{
          firstLineNumber: 1,
          tabSize: 2,
          showLineNumbers: false,
          showFoldWidgets: false,
        }}
        className="ace-editor"
      />
    );
  };
}
