import { HoverablePopover, Markdown, Placement } from 'core/presentation';
import { isUndefined } from 'lodash';
import React from 'react';
import ReactGA from 'react-ga';

import { HelpTextExpandedContext } from './HelpTextExpandedContext';
import { HelpContentsRegistry } from './helpContents.registry';

export interface IHelpFieldProps {
  id?: string;
  fallback?: string;
  content?: string;
  placement?: Placement;
  expand?: boolean;
  label?: string;
}

function HelpFieldContents(props: Pick<IHelpFieldProps, 'id' | 'fallback' | 'content'>): JSX.Element {
  const { id, fallback, content } = props;

  let contentString = content;
  if (id && !contentString) {
    contentString = HelpContentsRegistry.getHelpField(id) || fallback;
  }

  const config = { ADD_ATTR: ['target'] }; // allow: target="_blank"
  return <Markdown message={contentString} options={config} trim={true} />;
}

export function HelpField(props: IHelpFieldProps) {
  const { content, expand, fallback, id, label, placement } = props;

  const [popoverShownStart, setPopoverShownStart] = React.useState<number>();
  const onShow = (): void => setPopoverShownStart(Date.now());
  const onHide = (): void => {
    if (Date.now() - popoverShownStart > 500) {
      ReactGA.event({ action: 'Help contents viewed', category: 'Help', label: props.id || props.content });
    }
  };

  const icon = <i className="small glyphicon glyphicon-question-sign" />;
  const shouldExpandFromContext = React.useContext(HelpTextExpandedContext);
  const expandHelpText = isUndefined(expand) ? shouldExpandFromContext : expand;

  const contents = <HelpFieldContents content={content} fallback={fallback} id={id} />;
  const popover = (
    <HoverablePopover placement={placement || 'top'} template={contents} onShow={onShow} onHide={onHide}>
      <span className="clickable help-field"> {label || icon} </span>
    </HoverablePopover>
  );

  if (label) {
    return <div className="text-only">{!expandHelpText && contents && popover}</div>;
  } else {
    const expanded = <div className="help-contents small"> {contents} </div>;

    return (
      <div style={{ display: 'inline-block' }}>
        {!expandHelpText && contents && popover}
        {expandHelpText && contents && expanded}
      </div>
    );
  }
}
