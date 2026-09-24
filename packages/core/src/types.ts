// Serialized JSON contract type shapes (consumed by validator and emitted by serialize)

export interface TypeModifiers {
  optional?: true;
  default?: string | number | boolean;
}

export interface TString extends TypeModifiers {
  kind: "string";
}

export interface TInt extends TypeModifiers {
  kind: "int";
}

export interface TLong extends TypeModifiers {
  kind: "long";
}

export interface TDouble extends TypeModifiers {
  kind: "double";
}

export interface TBool extends TypeModifiers {
  kind: "bool";
}

export interface TItem extends TypeModifiers {
  kind: "item";
}

// The value is the name of one of the screen's declared assets, never a hash or a URL.
export interface TAsset extends TypeModifiers {
  kind: "asset";
}

export interface TList extends TypeModifiers {
  kind: "list";
  of: TypeSchema;
}

export interface TObject extends TypeModifiers {
  kind: "object";
  fields: Record<string, TypeSchema>;
}

export type TypeSchema = TString | TInt | TLong | TDouble | TBool | TItem | TAsset | TList | TObject;

export type AssetType = "image/png" | "image/jpeg" | "image/gif" | "font/ttf" | "font/otf";

// An entry of the contract's asset table, keyed by the file's sha256. Images carry their dimensions so the client can
// lay them out and reject them before downloading.
export interface AssetInfo {
  type: AssetType;
  bytes: number;
  width?: number;
  height?: number;
  frames?: number;
}

// What importing an asset file evaluates to: the reference a prop uses plus the entry for the asset table.
export interface AssetRef extends AssetInfo {
  $asset: string;
}

export interface ComponentNode {
  type: string;
  props: Record<string, unknown>;
  children: ComponentNode[];
}

export interface Contract {
  schemaVersion: number;
  id: string;
  properties: Record<string, TypeSchema>;
  actions: Record<string, TypeSchema>;
  root: ComponentNode;
  assets?: Record<string, AssetInfo>;
  assetNames?: Record<string, string>;
}
