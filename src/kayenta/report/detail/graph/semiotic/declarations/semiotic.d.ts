declare module 'semiotic' {
  import { CurveFactory } from 'd3-shape';
  import { ScaleTime } from 'd3-scale';

  export class XYFrame extends React.Component<IXYFrameProps<DataSet, DataPoint>> {}
  export class MinimapXYFrame extends React.Component<IMinimapProps<DataSet, DataPoint>> {}
  export class OrdinalFrame extends React.Component {}
  export class Annotation extends React.Component<IAnnotationProps> {}
  export class Axis extends React.Component<IAxisProps> {}

  // Props for the XYFrame component
  export interface IXYFrameProps<DataSet, DataPoint> {
    lines?: DataSet[];
    lineType?: { type: string; interpolator: CurveFactory };
    lineStyle?: (ds: DataSet) => object;
    xAccessor?: string | ((d: DataPoint) => Date);
    yAccessor?: string | ((d: DataPoint) => number);
    xScaleType?: ScaleTime<number, number>;
    baseMarkProps?: object;
    hoverAnnotation?: boolean | (object | Function)[] | object | Function;
    customHoverBehavior?: (d: IXYFrameHoverBaseArgs<DataPoint> & DataPoint) => void;
    xExtent?: Date[] | number[];
    axes?: object[];
    margin?: IMargin;
    matte?: boolean;
    size?: number[];
    yBrushable?: boolean;
    brushEnd?: (d: Date[]) => void;
  }

  // Props for the Minimap component
  export interface IMinimapProps<DataSet, DataPoint> extends IXYFrameProps<DataSet, DataPoint> {
    minimap?: IXYFrameProps<DataSet, DataPoint>;
  }

  // Props for the Annotation component
  export interface IAnnotationProps {
    noteData: {
      x: number;
      y: number;
      dx?: number;
      dy?: number;
      nx?: number;
      ny?: number;
      note: {
        label: string;
        wrap?: number;
        align?: string;
        orientation?: string;
        padding?: number;
        color?: string;
        lineType?: string;
      };
      className?: string;
    };
  }

  // Props for the Axis component
  export interface IAxisProps {
    className?: string;
    size: number[];
    scale: ScaleTime;
    orient?: string;
    label?: string | object;
    tickValues?: number[] | Date[];
    tickFormat?: (d: number) => JSX.Element | string;
    ticks?: number;
  }

  // Args supplied by semiotic's XYFrame to client's hover event callback function
  export interface IXYFrameHoverBaseArgs<DataPoint> {
    data: DataPoint;
    points?: DataPoint[];
    voronoiX: number;
    voronoiY: number;
  }

  /*
  * Args supplied by semiotic's OrdinalFrame to client's custom hover event callback function
  */
  export interface IOrFrameHoverArgs<DataPoint> {
    column?: IOrGroup<DataPoint>;
    summary?: IOrPiece<DataPoint>[];
    type?: string; // type of hover event (e.g. frame-hover)
    points: undefined | IOrSummaryPiece[]; // used for calculated summary datasets (e.g. boxplot)
    voronoiX?: number; // x position on the SVG element
    voronoiY?: number; // y position on the SVG element
  }

  export interface IOrSummaryPiece {
    isSummaryData: boolean;
    key: string; // data group (i.e. baseline or canary)
    label: string;
    summaryPieceName: string;
    type: string; // type of hover event (e.g. frame-hover)
    value: number;
    x: number; // x position on the SVG element
    y: number; // y position on the SVG element
  }

  export interface IOrPiece<DataPoint> {
    base: number; // coordinate of the base along the y-axis
    value: number; // original value of the data point
    scaledValue: number; // value in pixels
    data: DataPoint;
  }

  export interface IOrGroup<DataPoint> {
    middle: number; // absolute position of the center of the column along the main axis
    name: string; // ordinal label
    padding: number;
    width: number; // width of the column
    x: number; // starting position of the column (along the main axis)
    xyData: IOrXyData<DataPoint>[];
    y: number;
  }

  export interface IOrXyData<DataPoint> {
    o: string; // ordinal label for this data
    xy: {
      height: number; //height of the data point svg along the y axis
      width: number; // width of the data point svg (e.g. bar width)
      x: number; // starting x coordinate of the drawn data svg
      y: number; // starting y coordinate of the drawn data svg

      // center coordinate of the data point svg along the main (x) axis
      // relative to x
      middle: number;
    };
    piece: IOrPiece<DataPoint>;
  }

  /*
  * Args supplied by semiotic's Frame to client's custom annotation function
  */
  export interface ISemioticAnnotationArgs<AnnotationData, CategoryData> {
    d: AnnotationData; // client-defined custom annotation data
    i: number; // index
    categories: {
      [label: string]: CategoryData;
    };
    orFrameState?: ISemioticOrFrameState;
  }

  export interface ISemioticOrFrameState {
    pieceDataXY: IOrSummaryPiece[];
  }

  // Frame prop that specifies any pre-defined semiotic annotation
  export type IAnnotationType = {
    type?: string | Function;
    column?: { name: string };
    x?: number;
    y?: number;
    yTop?: number;
    yBottom?: number;
    yMiddle?: number;
    coordinates?: object[];
  };
}
