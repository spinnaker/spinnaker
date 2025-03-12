import React from 'react';

export interface IRenderWhenVisibleProps {
  // not valid without a container in px (IntersectionObserver doesn't do so good with null root but non-null rootMargin)
  bufferHeight?: number;
  // best guess to height of non-rendered content (in px)
  placeholderHeight: number;
  // set to false to call render eagerly
  initiallyVisible?: boolean;
  // standard render method; receives no props
  render: () => JSX.Element;
  // optional HTML element to use as the root in the IntersectionObserver
  container?: Element;
  // turns off de-rendering content
  disableHide?: boolean;
}

export const RenderWhenVisible = ({
  bufferHeight = 0,
  initiallyVisible,
  placeholderHeight,
  render,
  container,
  disableHide,
}: IRenderWhenVisibleProps) => {
  const [isVisible, setIsVisible] = React.useState(!!initiallyVisible);
  const [height, setHeight] = React.useState(placeholderHeight);
  let observer: IntersectionObserver;

  const nodeRef = React.useRef<HTMLDivElement>();

  React.useEffect(() => {
    let visible = isVisible;
    observer = new IntersectionObserver(
      (entries) => {
        const inView = entries[0].isIntersecting;
        if (inView && !visible) {
          visible = true;
          setIsVisible(true);
        }
        if (!inView && visible && !disableHide) {
          visible = false;
          setHeight(entries[0].boundingClientRect.height);
          setIsVisible(false);
        }
      },
      {
        root: container,
        threshold: [0],
        rootMargin: bufferHeight && container ? `${bufferHeight}px 0px` : '0px',
      },
    );
    observer.observe(nodeRef.current);
    return () => {
      observer.disconnect();
    };
  }, []);

  return (
    <div ref={nodeRef} className="render-when-visible">
      {isVisible && render()}
      {!isVisible && <div style={{ height: height + 'px' }} />}
    </div>
  );
};
