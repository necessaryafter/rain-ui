import * as path from "path";

const NONDETERMINISTIC_CALLS = [
  { label: "Math.random()", pattern: /\bMath\s*\.\s*random\s*\(/ },
  { label: "Date.now()", pattern: /\bDate\s*\.\s*now\s*\(/ },
  { label: "new Date()", pattern: /\bnew\s+Date\s*\(/ },
];

const transpilers = {
  ts: new Bun.Transpiler({ loader: "ts" }),
  tsx: new Bun.Transpiler({ loader: "tsx" }),
  js: new Bun.Transpiler({ loader: "js" }),
  jsx: new Bun.Transpiler({ loader: "jsx" }),
};

export interface DeterminismWarning {
  file: string;
  line: number | undefined;
  call: string;
}

// Walks the local import graph of each entry. Bare specifiers are dependencies (node_modules) and are not followed.
export async function findNondeterministicCalls(entries: string[]): Promise<DeterminismWarning[]> {
  const visited = new Set<string>();
  const pending = [...entries];
  const warnings: DeterminismWarning[] = [];

  while (pending.length > 0) {
    const file = pending.pop()!;
    if (visited.has(file)) continue;

    visited.add(file);

    const transpiler = transpilerFor(file);
    if (!transpiler) continue;

    const source = await Bun.file(file).text();
    const code = transpiler.transformSync(source);

    warnings.push(...scanCalls(file, source, code));
    pending.push(...localImports(file, transpiler.scan(source).imports));
  }

  return warnings.sort((a, b) => a.file.localeCompare(b.file) || a.call.localeCompare(b.call));
}

// Matches against the transpiled code, which has no comments or types, and only uses the source for the line number.
function scanCalls(file: string, source: string, code: string): DeterminismWarning[] {
  const lines = source.split("\n");
  const warnings: DeterminismWarning[] = [];

  for (const { label, pattern } of NONDETERMINISTIC_CALLS) {
    if (!pattern.test(code)) continue;

    const index = lines.findIndex((line) => pattern.test(line));
    warnings.push({
      file,
      line: index === -1 ? undefined : index + 1,
      call: label,
    });
  }

  return warnings;
}

function localImports(file: string, imports: { path: string }[]): string[] {
  const resolved: string[] = [];

  for (const { path: specifier } of imports) {
    if (!isLocalSpecifier(specifier)) continue;

    try {
      resolved.push(Bun.resolveSync(specifier, path.dirname(file)));
    } catch {
      // The import itself fails later with a proper error when the screen is loaded.
    }
  }

  return resolved;
}

function isLocalSpecifier(specifier: string): boolean {
  return specifier.startsWith("./") || specifier.startsWith("../") || path.isAbsolute(specifier);
}

function transpilerFor(file: string): Bun.Transpiler | undefined {
  const extension = path.extname(file).slice(1);

  return transpilers[extension as keyof typeof transpilers];
}
