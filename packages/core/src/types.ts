// Serialized JSON contract type shapes (consumed by validator and emitted by serialize)

export interface TypeSchemaBase {
    kind: string;
}

export interface TString extends TypeSchemaBase {
    kind: "string";
}

export interface TInt extends TypeSchemaBase {
    kind: "int";
}

export interface TLong extends TypeSchemaBase {
    kind: "long";
}

export interface TBool extends TypeSchemaBase {
    kind: "bool";
}

export interface TItem extends TypeSchemaBase {
    kind: "item";
}

export interface TList extends TypeSchemaBase {
    kind: "list";
    of: TypeSchema;
}

export interface TObject extends TypeSchemaBase {
    kind: "object";
    fields: Record<string, TypeSchema>;
}

export type TypeSchema = TString | TInt | TLong | TBool | TItem | TList | TObject;

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
