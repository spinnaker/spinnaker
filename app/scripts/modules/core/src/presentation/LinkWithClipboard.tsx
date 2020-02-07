import React from 'react';
import { CopyToClipboard } from 'core/utils';

export interface ILinkWithClipboardProps {
  url: string;
  text: string;
}

export const LinkWithClipboard = ({ url, text }: ILinkWithClipboardProps) => (
  <>
    <a href={url} target="_blank">
      {text}
    </a>
    <CopyToClipboard text={text} toolTip="'Copy to clipboard'" />
  </>
);
