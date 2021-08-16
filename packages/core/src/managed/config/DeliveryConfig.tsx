import React from 'react';
import AceEditor from 'react-ace';
import { RelativeTimestamp } from '../RelativeTimestamp';

const DeliveryConfigContentRenderer = ({ content }: { content: string }) => {
  return (
    <AceEditor
      mode="yaml"
      theme="textmate"
      readOnly
      fontSize={12}
      cursorStart={0}
      showPrintMargin={false}
      highlightActiveLine={true}
      maxLines={Infinity}
      value={content}
      setOptions={{
        firstLineNumber: 1,
        tabSize: 2,
        showLineNumbers: true,
        showFoldWidgets: true,
      }}
      style={{ width: 'auto' }}
      className="ace-editor sp-margin-s-top"
      editorProps={{ $blockScrolling: true }}
      onLoad={(editor) => {
        // This removes the built-in search box (as it doesn't scroll properly to matches)
        // commands is missing in the type def and therefore we have to cast as any
        (editor as any).commands?.removeCommand('find');
      }}
    />
  );
};

interface IDeliveryConfigProps {
  config?: string;
  updatedAt?: string;
  isProcessed?: boolean;
}

export const DeliveryConfig: React.FC<IDeliveryConfigProps> = ({ config, updatedAt, isProcessed, children }) => {
  if (!config) return null;
  return (
    <div className="sp-margin-xl-top">
      <div className="sp-margin-m-bottom">
        <h4 className="sp-margin-3xs-bottom">{isProcessed ? 'Processed Delivery Config' : 'Delivery config'}</h4>

        {updatedAt && (
          <small>
            Last update: <RelativeTimestamp timestamp={updatedAt} withSuffix removeStyles />
          </small>
        )}
      </div>
      {children}
      <DeliveryConfigContentRenderer content={config} />
    </div>
  );
};
