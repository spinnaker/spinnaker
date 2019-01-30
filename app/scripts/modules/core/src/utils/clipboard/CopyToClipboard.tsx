import * as React from 'react';
import * as ReactGA from 'react-ga';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import { PositionProperty } from 'csstype';
import { padStart, padEnd } from 'lodash';

import './CopyToClipboard.less';

export interface ICopyToClipboardProps {
  analyticsLabel?: string;
  displayText: boolean;
  text: string;
  toolTip: string;
}

interface IInputStyle {
  backgroundColor: string;
  borderWidth: string;
  display: string;
  height?: string;
  marginLeft?: string;
  overflow?: string;
  position?: PositionProperty;
}

interface ICopyToClipboardState {
  tooltipCopy: boolean | string;
  inputWidth: number | 'auto';
}

/**
 * Places text in an invisible input field so we can auto-focus and select the text
 * then copy it to the clipboard onClick. Used in labels found in components like
 * ManifestStatus to make it easier to grab data from the UI.
 *
 * This component mimics utils/clipboard/copyToClipboard.component.ts but
 * since the text is placed in an invisible input its very easy to select
 * if the copy fails.
 */
export class CopyToClipboard extends React.Component<ICopyToClipboardProps, ICopyToClipboardState> {
  public static defaultProps = {
    displayText: false,
  };

  // Handles onto our DOM elements. We need to select data from the input
  // and use the hiddenRef span to measure the width of our text
  private inputRef: React.RefObject<HTMLInputElement> = React.createRef();
  private hiddenRef: React.RefObject<HTMLSpanElement> = React.createRef();

  private inputStyle: IInputStyle = {
    backgroundColor: 'transparent',
    borderWidth: '0px',
    display: 'inline-block',
  };

  private hiddenStyle = {
    height: 0,
    overflow: 'hidden',
    position: 'absolute' as 'absolute',
    whiteSpace: 'pre' as 'pre',
  };

  constructor(props: ICopyToClipboardProps) {
    super(props);
    this.state = {
      tooltipCopy: false,
      inputWidth: 'auto',
    };
  }

  /**
   * We need to play some games to get the correct width of the container
   * input element but grabbing the offsetWidth of a hidden span containing
   * the same value text as the input.
   */
  public componentDidMount() {
    const { displayText } = this.props;
    if (displayText) {
      const hiddenNode = this.hiddenRef.current;
      this.setState({ inputWidth: hiddenNode.offsetWidth + 3 });
    }
  }

  /**
   * Focuses on the input element and attempts to copy to the clipboard.
   * Also updates state.tooltipCopy with a success/fail message, which is
   * reset after 3s. The selection is immediately blur'd so you shouldn't
   * see much of it during the copy.
   */
  public handleClick = (e: React.SyntheticEvent): void => {
    e.preventDefault();

    const { analyticsLabel, toolTip, text } = this.props;
    ReactGA.event({
      category: 'Copy to Clipboard',
      action: 'copy',
      label: analyticsLabel || text,
    });

    const node: HTMLInputElement = this.inputRef.current;
    node.focus();
    node.select();

    // A best attempt at trying to keep the Copied! text centered in the
    // Tooltip, otherwise it jumps around.
    let copiedText = 'Copied!';

    const toolTipPadding = Math.round(Math.max(0, toolTip.length - copiedText.length) / 2);
    copiedText = padStart(copiedText, copiedText.length + toolTipPadding, '\u2007');
    copiedText = padEnd(copiedText, copiedText.length + toolTipPadding, '\u2007');

    try {
      document.execCommand('copy');
      node.blur();
      this.setState({ tooltipCopy: copiedText });
      window.setTimeout(this.resetToolTip, 3000);
    } catch (e) {
      this.setState({ tooltipCopy: "Couldn't copy!" });
    }
  };

  public resetToolTip = () => {
    this.setState({ tooltipCopy: false });
  };

  public render() {
    const { displayText, toolTip, text = '' } = this.props;
    const { inputWidth, tooltipCopy } = this.state;

    const persistOverlay = Boolean(tooltipCopy);
    const copy = tooltipCopy || toolTip;
    const id = `clipboardValue-${text.replace(' ', '-')}`;
    const tooltipComponent = <Tooltip id={id}>{copy}</Tooltip>;

    let updatedStyle = {
      ...this.inputStyle,
      width: inputWidth,
    };

    if (!displayText) {
      updatedStyle = {
        ...updatedStyle,
        position: 'absolute' as 'absolute',
        height: '0px',
        marginLeft: '-9999px',
        overflow: 'hidden',
      };
    }

    return (
      <React.Fragment>
        {displayText && (
          <span ref={this.hiddenRef} style={this.hiddenStyle}>
            {text}
          </span>
        )}
        <input
          onChange={e => e} // no-op to prevent warnings
          ref={this.inputRef}
          value={text}
          type="text"
          style={updatedStyle}
        />
        <OverlayTrigger defaultOverlayShown={persistOverlay} placement="top" overlay={tooltipComponent} delayHide={250}>
          <button
            onClick={this.handleClick}
            className="btn btn-xs btn-default clipboard-btn"
            uib-tooltip={toolTip}
            aria-label="Copy to clipboard"
          >
            <span className="glyphicon glyphicon-copy" />
          </button>
        </OverlayTrigger>
      </React.Fragment>
    );
  }
}
