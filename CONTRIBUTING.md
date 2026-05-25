# Contributing to conditionally-execute

Thank you for considering contributing to conditionally-execute — the enterprise-grade
solution for developers who find `if` statements too concise.

## Development setup

```bash
git clone https://github.com/bopke/conditionally-execute
cd conditionally-execute
npm install
```

## Available scripts

| Script | Description |
|--------|-------------|
| `npm run build` | Compile TypeScript source to `dist/` |
| `npm run typecheck` | Type-check without emitting files |
| `npm run lint` | Lint source and tests |
| `npm run lint:fix` | Lint and auto-fix where possible |
| `npm test` | Run the test suite |
| `npm run bench` | Run the performance benchmark |

## Project structure

```
conditionally-execute/
├── src/
│   └── index.ts        # TypeScript source
├── dist/               # Compiled output (gitignored, built by CI)
├── .github/
│   ├── ISSUE_TEMPLATE/ # Bug report and feature request templates
│   ├── workflows/      # GitHub Actions CI configuration
│   └── dependabot.yml  # Automated dependency updates
├── test.js             # Mocha test suite
├── bench.js            # Performance benchmark
├── tsconfig.json       # TypeScript compiler configuration
├── .eslintrc.js        # ESLint configuration
├── .prettierrc         # Prettier code style configuration
└── .editorconfig       # Editor settings
```

## Submitting changes

1. Fork the repository
2. Create a branch: `git checkout -b my-feature`
3. Make your changes
4. Ensure tests pass: `npm test`
5. Ensure linting passes: `npm run lint`
6. Update `CHANGELOG.md` under `[Unreleased]`
7. Open a Pull Request — fill out the PR template

## Commit message convention

```
type(scope): short description

longer description if needed
```

Types: `feat`, `fix`, `docs`, `chore`, `test`, `ci`, `refactor`

Example: `fix(condition): last condition() call now overwrites previous value`

## Code style

This project uses Prettier for formatting and ESLint for linting.
Both run automatically if you use an editor that supports `.editorconfig`
and `.prettierrc`. You can also run `npm run lint:fix` before committing.

## Questions?

Open an issue or start a Discussion on GitHub.
