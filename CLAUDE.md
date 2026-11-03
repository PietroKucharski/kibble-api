# Commit rules

- Follow Conventional Commits, without parentheses: `<type>: <description>` (e.g. `feat: add user registration`). Never use a scope like `feat(auth):`.
- Allowed types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert. Use `!` before the colon for breaking changes.
- The message is a single line only: no body, no lists, no bullet points, no trailers (no Co-Authored-By or similar).
- Do not use parentheses anywhere in the message.
- Enforced locally by `.githooks/commit-msg`. After cloning, run `git config core.hooksPath .githooks`.

# Branches

- `main`: stable branch.
- `development`: integration branch; work is branched from and merged into it.
