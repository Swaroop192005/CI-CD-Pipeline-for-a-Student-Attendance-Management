# Contributing Guide and Branch Policy

This file is the working agreement for the repository. It is enforced in
review, and the pipeline depends on it (Jenkins builds `develop` and
`feature/*`, and only `main` is released from).

---

## 1. Branching model

A trunk-plus-develop model: `main` is always releasable, `develop` is the
integration branch, and all work happens on short-lived branches.

| Branch | Purpose | Who merges into it | Protected |
|---|---|---|---|
| `main` | Released, tagged, always deployable | Release merge from `develop` only | Yes |
| `develop` | Integration of completed work | Pull request from a `feature/*`, `bugfix/*` or `chore/*` branch | Yes |
| `feature/*` | One backlog item | — (deleted after merge) | No |
| `bugfix/*` | A defect against `develop` | — | No |
| `hotfix/*` | An urgent defect against `main` | — | No |
| `chore/*` | Build, docs or tooling with no functional change | — | No |
| `release/*` | Release stabilisation when needed | — | No |

### Flow

```
main ──┬────────────────────────────────────────────── v1.0.0 ──▶
       │
     develop ──┬──────────────┬─────────────────────────▶
                │              │
       feature/attendance-core  feature/status-workflow-dashboard
```

1. Branch from `develop`.
2. Commit in small, reviewable steps.
3. Push and open a pull request into `develop`.
4. CI must be green and review comments resolved.
5. Merge, then delete the branch.
6. `develop` → `main` at a release point, then tag.

## 2. Branch naming rules

```
<type>/<short-kebab-case-description>
```

| Rule | Detail |
|---|---|
| Type prefix | One of `feature`, `bugfix`, `hotfix`, `chore`, `release`, `docs`, `experiment` |
| Separator | A single `/` after the type |
| Description | Lower-case kebab-case, 2–5 words, describing the outcome |
| Length | Maximum 60 characters total |
| Charset | `a-z`, `0-9`, `-` and the single `/` only |
| Issue reference | Optional suffix `-#<issue>`, e.g. `feature/attendance-search-#14` |

**Valid**

```
feature/attendance-core
feature/status-workflow-dashboard
bugfix/duplicate-record-check
chore/jenkins-pipeline
docs/stage-09-test-plan
```

**Invalid**

```
Feature/AttendanceCore        uppercase, no kebab-case
my-branch                     no type prefix
feature/fix                   says nothing about the outcome
feature/attendance_core       underscores
```

## 3. Commit message convention

Conventional Commits, so that history is scannable and a changelog can be
generated from it.

```
<type>(<scope>): <imperative summary, <= 72 chars>

<body: what changed and why, wrapped at 72 columns>

<footer: Refs #<issue>>
```

Types: `feat`, `fix`, `docs`, `test`, `build`, `ci`, `refactor`, `perf`,
`chore`.

Scopes used in this repository: `domain`, `service`, `web`, `api`,
`security`, `dashboard`, `search`, `workflow`, `build`, `ci`, `docker`,
`ansible`, `selenium`, `docs`.

**Good**

```
feat(workflow): enforce role-based status transitions

Route every workflow status change through WorkflowService so that
DRAFT -> SUBMITTED -> APPROVED/REJECTED is the only possible path and
each transition stores the acting user and a timestamp (FR-16..FR-21).

Refs #6
```

**Rejected in review**

```
update stuff
fix
WIP
```

Rules:

1. Imperative mood: "add", not "added" or "adds".
2. No trailing full stop in the summary.
3. The body explains *why*, since the diff already shows *what*.
4. One logical change per commit.
5. Never commit secrets, build output, or the H2 data directory.

## 4. Pull request rules

1. Target `develop` (or `main` only for a `hotfix/*`).
2. Fill in the pull request template completely.
3. Link the issue the branch implements.
4. CI must be green before merge.
5. At least one review comment must be raised and resolved; a PR merged
   with no review is a process failure.
6. Keep a PR under roughly 400 changed lines where possible.
7. Delete the branch after merge.

## 5. Definition of Done

A change is Done only when it satisfies every criterion in
`docs/stage-02-agile-planning.md` §4 — including that the stage
documentation and the captured evidence under `proofs/` are committed.

## 6. Issue labels

| Label | Meaning |
|---|---|
| `stage-01` … `stage-15` | The project task the issue belongs to |
| `type:feature` / `type:bug` / `type:chore` / `type:docs` | Nature of the work |
| `priority:must` / `priority:should` / `priority:could` | MoSCoW priority |
| `blocked` | Waiting on something; the blocker is named in a comment |
| `scope-change` | Proposes a change to the frozen MVP scope |

## 7. Local checks before pushing

```bash
mvn -B clean verify          # compile, unit tests, integration tests, coverage
git log --oneline -5         # confirm the commit messages read well
git diff --stat origin/develop...HEAD
```

A push that breaks `develop` is reverted first and diagnosed afterwards.
