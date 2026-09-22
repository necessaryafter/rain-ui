export const version = "1.0.0";

// Re-export all M1 Phase B implementation
export { t, defineProperties, defineActions, defineScreen, isScreenDefinition } from "./builder";
export type { ScreenDefinition } from "./builder";

export { createPropertyProxy, createActionProxy } from "./binding";
export type { Binding, ActionRef } from "./binding";

export { validateContract } from "./validate";
export type { ValidationResult } from "./validate";

export { compileScreen } from "./serialize";
export type { CompiledScreen } from "./serialize";

export type { TypeSchema, ComponentNode, Contract } from "./types";

// Validation error codes
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
