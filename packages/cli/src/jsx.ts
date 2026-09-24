import { readAsset } from "./assets";

const JSX_FACTORY = "__rain_h";
const JSX_FRAGMENT = "__rain_Fragment";

// Bun.Transpiler's automatic runtime emits jsxDEV calls without importing them, so the classic runtime is used with a
// known factory name, and the prelude adapts classic calls to @rain-ui/core's automatic-runtime jsx(). Kept on a
// single line so it does not shift line numbers by more than the first line.
const PRELUDE =
  `import { jsx as __rain_jsx, Fragment as ${JSX_FRAGMENT} } from "@rain-ui/core/jsx-runtime";` +
  `const ${JSX_FACTORY} = (type, props, ...children) => {` +
  `const { key, ...rest } = props ?? {};` +
  `if (children.length === 0) return __rain_jsx(type, rest);` +
  `return __rain_jsx(type, { ...rest, children: children.length === 1 ? children[0] : children });` +
  `};`;

const transpiler = new Bun.Transpiler({
  loader: "tsx",
  tsconfig: {
    compilerOptions: {
      jsx: "react",
      jsxFactory: JSX_FACTORY,
      jsxFragmentFactory: JSX_FRAGMENT,
    },
  },
});

// Any local import whose extension is not code or JSON is an asset; unsupported formats fail there with
// UNSUPPORTED_ASSET instead of Bun loading them as a file path. Dependencies in node_modules are left alone.
const ASSET_FILTER = /^(?!.*[\\/]node_modules[\\/]).*\.(?![cm]?[jt]sx?$|json$)[^./\\]+$/;

// Registered by the CLI itself so screens build the same way whatever tsconfig is next to the caller's cwd.
export function registerScreenLoaders(): void {
  Bun.plugin({
    name: "rain-jsx",
    setup(build) {
      build.onLoad({ filter: /\.(tsx|jsx)$/ }, async ({ path }) => ({
        contents: PRELUDE + transpiler.transformSync(await Bun.file(path).text()),
        loader: "js",
      }));

      build.onLoad({ filter: ASSET_FILTER }, ({ path }) => ({
        contents: `export default ${JSON.stringify(readAsset(path))};`,
        loader: "js",
      }));
    },
  });
}
