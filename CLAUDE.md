# Commit rules

- Follow Conventional Commits, without parentheses: `<type>: <description>` (e.g. `feat: add user registration`). Never use a scope like `feat(auth):`.
- Allowed types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert. Use `!` before the colon for breaking changes.
- The message is a single line only: no body, no lists, no bullet points, no trailers (no Co-Authored-By or similar).
- Do not use parentheses anywhere in the message.
- Enforced locally by `.githooks/commit-msg`. After cloning, run `git config core.hooksPath .githooks`.

# Branches

- `main`: stable branch.
- `development`: integration branch; work is branched from and merged into it.

# Branch naming

- Every commit must be made on its own dedicated branch, created from `development`. Never commit directly to `main` or `development`.
- Branch name pattern: `type/description[-identifier]`, in lowercase kebab-case (e.g. `feat/user-registration`, `fix/login-error-KIB-12` written as `fix/login-error-kib-12`).
- `type` must be the same type used in the commit message. `description` is a short summary and `identifier` is an optional ticket or issue id.
- Enforced locally by `.githooks/pre-commit` and `.githooks/commit-msg`.
