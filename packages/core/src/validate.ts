import type { Contract, ComponentNode, TypeSchema } from "./types";
import { ValidationErrorCode, Limits } from "./index";

export interface ValidationError {
    code: ValidationErrorCode;
    path: string;
}

export type ValidationResultOk = { ok: true };
export type ValidationResultErr = { ok: false; error: ValidationError };
export type ValidationResult = ValidationResultOk | ValidationResultErr;

const COMPONENT_TYPES = new Set(["column", "row", "text", "item", "button", "list"]);
const ID_PATTERN = /^[a-z0-9_-]+:[a-z0-9_/-]+$/;

export function validateContract(json: unknown, options?: { sourceBytes?: number }): ValidationResult {
    if (typeof json !== "object" || json === null) {
        return { ok: false, error: { code: "UNKNOWN_SCHEMA_VERSION", path: "root" } };
    }

    const contract = json as any;

    // Check contract byte size (Fix C)
    const sourceBytes = options?.sourceBytes ?? Buffer.byteLength(JSON.stringify(contract), "utf-8");
    if (sourceBytes > Limits.MAX_CONTRACT_BYTES) {
        return { ok: false, error: { code: "LIMIT_EXCEEDED", path: "root" } };
    }

    // Check basic constraints
    if (contract.schemaVersion !== 0) {
        return { ok: false, error: { code: "UNKNOWN_SCHEMA_VERSION", path: "schemaVersion" } };
    }

    if (!ID_PATTERN.test(contract.id)) {
        return { ok: false, error: { code: "INVALID_ID", path: "id" } };
    }

    if (!isObject(contract.properties)) {
        return { ok: false, error: { code: "UNKNOWN_SCHEMA_VERSION", path: "properties" } };
    }

    if (!isObject(contract.actions)) {
        return { ok: false, error: { code: "UNKNOWN_SCHEMA_VERSION", path: "actions" } };
    }

    if (Object.keys(contract.actions).length > Limits.MAX_ACTIONS) {
        return { ok: false, error: { code: "LIMIT_EXCEEDED", path: "actions" } };
    }

    // Validate action IDs
    for (const actionId of Object.keys(contract.actions)) {
        if (!ID_PATTERN.test(actionId)) {
            return { ok: false, error: { code: "INVALID_ID", path: "actions" } };
        }
    }

    // Validate component tree
    const validator = new ContractValidator(contract.properties, contract.actions);
    return validator.validateNode(contract.root, "root", contract.properties, 0);
}

class ContractValidator {
    private nodeCount = 0;

    constructor(private properties: Record<string, TypeSchema>, private actions: Record<string, TypeSchema>) {}

    validateNode(node: any, path: string, scope: Record<string, TypeSchema>, depth: number): ValidationResult {
    // Check limits
    this.nodeCount++;
    if (this.nodeCount > Limits.MAX_NODES) {
      return { ok: false, error: { code: "LIMIT_EXCEEDED", path } };
    }

    if (depth > Limits.MAX_DEPTH) {
      return { ok: false, error: { code: "LIMIT_EXCEEDED", path } };
    }

    // Check component type
    if (!COMPONENT_TYPES.has(node.type)) {
      return { ok: false, error: { code: "UNKNOWN_COMPONENT", path } };
    }

    // Validate props
    const propResult = this.validateProps(node, path, scope);
    if (!propResult.ok) return propResult;

    // Validate children
    const childResult = this.validateChildren(node, path, scope, depth);
    if (!childResult.ok) return childResult;

    return { ok: true };
    }

    private validateProps(node: any, path: string, scope: Record<string, TypeSchema>): ValidationResult {
        const ALLOWED_PROPS: Record<string, Set<string>> = {
            text: new Set(["value", "color", "align", "shadow"]),
            column: new Set(["gap", "padding", "align", "justify", "width", "height"]),
            row: new Set(["gap", "padding", "align", "justify", "width", "height"]),
            item: new Set(["value", "size"]),
            button: new Set(["action", "payload", "disabled"]),
            list: new Set(["source"]),
        };

        const allowedProps = ALLOWED_PROPS[node.type] || new Set();

        for (const propKey of Object.keys(node.props || {})) {
            if (!allowedProps.has(propKey)) {
                return { ok: false, error: { code: "UNKNOWN_PROP", path: `${path}.props.${propKey}` } };
            }

            const propValue = node.props[propKey];
            const propResult = this.validateProp(node.type, propKey, propValue, `${path}.props.${propKey}`, scope);
            if (!propResult.ok) return propResult;
        }

        // Fix B: validate button payload against action schema (paired validation)
        if (node.type === "button" && "payload" in (node.props || {})) {
            const payloadResult = this.validateButtonPayload(node, path, scope);
            if (!payloadResult.ok) return payloadResult;
        }

        return { ok: true };
    }

    private validateProp(componentType: string, propName: string, propValue: any, path: string, scope: Record<string, TypeSchema>): ValidationResult {
        switch (componentType) {
            case "text":
                if (propName === "value") {
                    return this.validateStringOrBinding(propValue, path, scope);
                }
                break;

            case "button":
                if (propName === "action") {
                    if (typeof propValue !== "string" || !this.actions[propValue]) {
                        return { ok: false, error: { code: propValue in this.actions ? "INVALID_PROP_TYPE" : "UNDECLARED_ACTION", path } };
                    }
                } else if (propName === "disabled") {
                    return this.validateBoolOrBinding(propValue, path, scope);
                } else if (propName === "payload") {
                    // Paired validation happens in validateProps after all per-key checks
                    return { ok: true };
                }
                break;

            case "item":
                if (propName === "value") {
                    return this.validateBinding(propValue, path, scope, (schema) => (schema as any).kind === "item");
                }
                break;

            case "list":
                if (propName === "source") {
                    return this.validateBinding(propValue, path, scope, (schema) => (schema as any).kind === "list");
                }
                break;
        }

        return { ok: true };
    }

    private validateStringOrBinding(propValue: any, path: string, scope: Record<string, TypeSchema>): ValidationResult {
    if (typeof propValue === "string") {
      if (propValue.length > Limits.MAX_STRING_LENGTH) {
        return { ok: false, error: { code: "LIMIT_EXCEEDED", path } };
      }
      return { ok: true };
    }

    if (isBinding(propValue)) {
      const bindPath = propValue.$bind;
      const schema = scope[bindPath];

      if (!schema) return { ok: false, error: { code: "UNDECLARED_BINDING", path } };
      if ((schema as any).kind !== "string") return { ok: false, error: { code: "BINDING_TYPE_MISMATCH", path } };

      return { ok: true };
    }

    return { ok: false, error: { code: "INVALID_PROP_TYPE", path } };
    }

    private validateBoolOrBinding(propValue: any, path: string, scope: Record<string, TypeSchema>): ValidationResult {
    if (typeof propValue === "boolean") {
      return { ok: true };
    }

    if (isBinding(propValue)) {
      const bindPath = propValue.$bind;
      const schema = scope[bindPath];
      if (!schema) return { ok: false, error: { code: "UNDECLARED_BINDING", path } };
      if ((schema as any).kind !== "bool") return { ok: false, error: { code: "BINDING_TYPE_MISMATCH", path } };
      return { ok: true };
    }

    return { ok: false, error: { code: "INVALID_PROP_TYPE", path } };
    }

    private validateBinding(
    propValue: any,
    path: string,
    scope: Record<string, TypeSchema>,
    typeCheck: (schema: TypeSchema) => boolean
    ): ValidationResult {
    if (!isBinding(propValue)) {
      return { ok: false, error: { code: "INVALID_PROP_TYPE", path } };
    }

    const bindPath = propValue.$bind;
    const schema = scope[bindPath];
    if (!schema) return { ok: false, error: { code: "UNDECLARED_BINDING", path } };
    if (!typeCheck(schema)) return { ok: false, error: { code: "BINDING_TYPE_MISMATCH", path } };

    return { ok: true };
    }

    private validateChildren(node: any, path: string, scope: Record<string, TypeSchema>, depth: number): ValidationResult {
        const children = node.children || [];

        for (let i = 0; i < children.length; i++) {
            const child = children[i];
            const childPath = `${path}.children[${i}]`;

            // Fix A: For list nodes, first child uses item scope (resolved from source binding)
            const childScope = node.type === "list" && i === 0
                ? this.resolveItemScope(node, scope) ?? scope
                : scope;

            const childResult = this.validateNode(child, childPath, childScope, depth + 1);
            if (!childResult.ok) return childResult;
        }

        return { ok: true };
    }

    private resolveItemScope(node: any, scope: Record<string, TypeSchema>): Record<string, TypeSchema> | null {
        const sourceBinding = node.props?.source;
        if (!isBinding(sourceBinding)) return null;

        const bindPath = sourceBinding.$bind;
        const sourceSchema = scope[bindPath];
        if (!sourceSchema) return null;

        const listSchema = sourceSchema as any;
        if (listSchema.kind !== "list") return null;

        const ofSchema = listSchema.of as any;
        if (ofSchema.kind !== "object") return null;

        return ofSchema.fields as Record<string, TypeSchema>;
    }

    private validateButtonPayload(node: any, path: string, scope: Record<string, TypeSchema>): ValidationResult {
        const actionId = node.props?.action;
        if (typeof actionId !== "string" || !this.actions[actionId]) {
            return { ok: false, error: { code: "PAYLOAD_SCHEMA_MISMATCH", path: `${path}.props.payload` } };
        }

        const actionSchema = this.actions[actionId];
        return this.validatePayloadValue(node.props.payload, actionSchema, `${path}.props.payload`, scope);
    }

    private validatePayloadValue(value: any, schema: TypeSchema, path: string, scope: Record<string, TypeSchema>): ValidationResult {
        if (isBinding(value)) {
            const bound = scope[(value as any).$bind];
            if (!bound) return { ok: false, error: { code: "UNDECLARED_BINDING", path } };
            if (!this.typeSchemasEqual(bound, schema)) return { ok: false, error: { code: "BINDING_TYPE_MISMATCH", path } };
            return { ok: true };
        }

        const schemaKind = (schema as any).kind;
        switch (schemaKind) {
            case "object": {
                if (!isObject(value)) return { ok: false, error: { code: "PAYLOAD_SCHEMA_MISMATCH", path } };
                const fields = (schema as any).fields as Record<string, TypeSchema>;
                for (const key of Object.keys(fields)) {
                    if (!(key in value)) return { ok: false, error: { code: "PAYLOAD_SCHEMA_MISMATCH", path: `${path}.${key}` } };
                }
                for (const key of Object.keys(value)) {
                    if (!(key in fields)) return { ok: false, error: { code: "PAYLOAD_SCHEMA_MISMATCH", path: `${path}.${key}` } };
                    const r = this.validatePayloadValue(value[key], fields[key], `${path}.${key}`, scope);
                    if (!r.ok) return r;
                }
                return { ok: true };
            }
            case "list": {
                if (!Array.isArray(value)) return { ok: false, error: { code: "PAYLOAD_SCHEMA_MISMATCH", path } };
                const ofSchema = (schema as any).of as TypeSchema;
                for (let i = 0; i < value.length; i++) {
                    const r = this.validatePayloadValue(value[i], ofSchema, `${path}[${i}]`, scope);
                    if (!r.ok) return r;
                }
                return { ok: true };
            }
            case "item":
                return { ok: false, error: { code: "PAYLOAD_SCHEMA_MISMATCH", path } };
            case "string":
                return typeof value === "string" ? { ok: true } : { ok: false, error: { code: "PAYLOAD_SCHEMA_MISMATCH", path } };
            case "bool":
                return typeof value === "boolean" ? { ok: true } : { ok: false, error: { code: "PAYLOAD_SCHEMA_MISMATCH", path } };
            case "int":
            case "long":
                return typeof value === "number" ? { ok: true } : { ok: false, error: { code: "PAYLOAD_SCHEMA_MISMATCH", path } };
        }

        return { ok: false, error: { code: "PAYLOAD_SCHEMA_MISMATCH", path } };
    }

    private typeSchemasEqual(a: TypeSchema, b: TypeSchema): boolean {
        const aKind = (a as any).kind;
        const bKind = (b as any).kind;
        if (aKind !== bKind) return false;

        if (aKind === "list") {
            return this.typeSchemasEqual((a as any).of, (b as any).of);
        }
        if (aKind === "object") {
            const aFields = (a as any).fields as Record<string, TypeSchema>;
            const bFields = (b as any).fields as Record<string, TypeSchema>;
            const aKeys = Object.keys(aFields).sort();
            const bKeys = Object.keys(bFields).sort();
            if (aKeys.length !== bKeys.length) return false;
            for (let i = 0; i < aKeys.length; i++) {
                if (aKeys[i] !== bKeys[i]) return false;
                if (!this.typeSchemasEqual(aFields[aKeys[i]], bFields[bKeys[i]])) return false;
            }
            return true;
        }
        return true;
    }
}

function isBinding(value: any): value is { $bind: string } {
    return typeof value === "object" && value !== null && typeof value.$bind === "string" && Object.keys(value).length === 1;
}

function isObject(value: any): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}
