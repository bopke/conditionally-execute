# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 2.x     | ✅ Yes             |
| 1.x     | ❌ No (EOL)        |

## Reporting a Vulnerability

If you discover a security vulnerability in conditionally-execute, please
**do not** open a public GitHub issue.

Instead, please report it privately via GitHub's
[private vulnerability reporting](https://github.com/bopke/conditionally-execute/security/advisories/new)
feature.

We will acknowledge receipt within 48 hours and aim to provide a fix or
mitigation within 7 days for confirmed vulnerabilities.

> Note: This library executes user-supplied callback functions. Callers are
> responsible for ensuring that functions passed to `onTrue()` and `onFalse()`
> do not introduce security vulnerabilities in their own application.
