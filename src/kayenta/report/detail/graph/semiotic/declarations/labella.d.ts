declare module 'labella' {
  export class Node<Data> {
    constructor(idealPos: number, width: number, data?: Data);
    readonly idealPos: number;
    readonly currentPos: number;
    readonly width: number;
    readonly data: Data;
    readonly layerIndex: number;
  }
  export class Force<Data> {
    constructor(options: { [option: string]: number | string | boolean | null });
    nodes(nodes: Node<Data>[]): this;
    nodes(): Node<Data>[];
    compute(): this;
  }
}
