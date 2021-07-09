import { HtmlRenderer, Parser } from 'commonmark';
import DOMPurify from 'dompurify';
import React from 'react';

import './Markdown.less';

export interface IMarkdownProps {
  [key: string]: any;

  /** markdown */
  message: string;

  /** optional tag */
  tag?: string;

  /** trim the contents before generating markdown; markdown treats four leading spaces as a <pre> tag, so if you are
   *  using string templates and the content starts on a new line, you probably want this set to true */
  trim?: boolean;

  /** The className(s) to apply to the tag (.Markdown class is always applied) */
  className?: string;

  /** Options passed to DOMPurify. Examples: https://github.com/cure53/DOMPurify/tree/master/demos#what-is-this */
  options?: IDOMPurifyConfig;
}

/**
 * Renders markdown into a div (or some other tag)
 * Extra props are passed through to the rendered tag
 */
export function Markdown(props: IMarkdownProps) {
  const { message, tag: tagProp, className: classNameProp, trim, tag = 'div', ...rest } = props;

  const parser = React.useMemo(() => new Parser(), []);
  const renderer = React.useMemo(() => new HtmlRenderer(), []);
  const className = `Markdown ${classNameProp || ''}`;

  if (message == null) {
    return null;
  }

  const parseable = trim ? message.trim() : message;

  const restProps = rest as React.DOMAttributes<any>;
  const parsed = parser.parse(parseable.toString());
  const rendered = renderer.render(parsed);
  restProps.dangerouslySetInnerHTML = { __html: DOMPurify.sanitize(rendered, props.options) };

  return React.createElement(tag, { ...restProps, className });
}

export const toMarkdown = (message: string, options: IDOMPurifyConfig = {}, inline = false) => (
  <Markdown message={message} tag={inline ? 'span' : 'div'} options={options} />
);
