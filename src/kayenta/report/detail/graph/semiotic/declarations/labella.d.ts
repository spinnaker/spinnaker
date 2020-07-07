declare module 'labella' {
  export class Node<Data> {
    public readonly idealPos: number;
    public readonly currentPos: number;
    public readonly width: number;
    public readonly data: Data;
    public readonly layerIndex: number;
    constructor(idealPos: number, width: number, data?: Data);
  }
  export class Force<Data> {
    constructor(options: { [option: string]: number | string | boolean | null });
    nodes(nodes: Array<Node<Data>>): this;
    nodes(): Array<Node<Data>>; // eslint-disable-line no-dupe-class-members
    compute(): this;
  }
}
