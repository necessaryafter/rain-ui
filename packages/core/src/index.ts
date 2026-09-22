// @rain-ui/core Phase A stubs
// These will be implemented in Phase B

export const version = "1.0.0";

// Schema builder stubs
export const t = {
  string: () => ({ kind: "string" as const }),
  int: () => ({ kind: "int" as const }),
  long: () => ({ kind: "long" as const }),
  bool: () => ({ kind: "bool" as const }),
  item: () => ({ kind: "item" as const }),
  list: (of: any) => ({ kind: "list" as const, of }),
  object: (fields: any) => ({ kind: "object" as const, fields }),
};

export function defineProperties(props: any) {
  return props;
}

export function defineActions(actions: any) {
  return actions;
}

export function defineScreen(def: any) {
  return def;
}

// Validation stub
export type ValidationErrorCode =
  | "UNKNOWN_SCHEMA_VERSION"
  | "UNKNOWN_COMPONENT"
  | "UNKNOWN_PROP"
  | "INVALID_PROP_TYPE"
  | "UNDECLARED_BINDING"
  | "BINDING_TYPE_MISMATCH"
  | "UNDECLARED_ACTION"
  | "PAYLOAD_SCHEMA_MISMATCH"
  | "DUPLICATE_SCREEN_ID"
  | "INVALID_ID"
  | "LIMIT_EXCEEDED";

export interface ValidationError {
  code: ValidationErrorCode;
  path: string;
}

// Limit constants (per spec §5.5)
export const Limits = {
  MAX_CONTRACT_BYTES: 256 * 1024,
  MAX_NODES: 2000,
  MAX_DEPTH: 32,
  MAX_ACTIONS: 256,
  MAX_STRING_LENGTH: 1024,
  MAX_PROPERTIES_BYTES: 256 * 1024,
  MAX_LIST_ELEMENTS: 1000,
  MAX_PAYLOAD_BYTES: 8 * 1024,
  MAX_PAYLOAD_DEPTH: 8,
  MAX_JSON_DEPTH: 32,
};

// TODO: Implement validateContract, serialize, binding proxy, etc. in Phase B
