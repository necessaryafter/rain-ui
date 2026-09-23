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

export interface TList extends TypeModifiers {
  kind: "list";
  of: TypeSchema;
}

export interface TObject extends TypeModifiers {
  kind: "object";
  fields: Record<string, TypeSchema>;
}

export type TypeSchema = TString | TInt | TLong | TDouble | TBool | TItem | TList | TObject;

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
}
