#!/usr/bin/env python3
"""Combine the per-task reports into one submission document.

Each stage keeps its own standalone report; this concatenates them with a
contents page and demotes their headings by one level so the combined file
has a single document title.
"""
import os
import re
import datetime

DOCS = "docs"
OUT = os.path.join(DOCS, "FINAL-REPORT.md")

SECTIONS = [
    ("00-environment-prerequisites.md",        "Environment Prerequisites"),
    ("stage-01-problem-definition.md",         "Task 1 — Problem Definition and Scope"),
    ("stage-02-agile-planning.md",             "Task 2 — Agile Planning and DevOps Workflow"),
    ("stage-03-requirements-architecture.md",  "Task 3 — Requirements, Architecture and Technology Setup"),
    ("stage-04-repository-initialisation.md",  "Task 4 — Git and GitHub Repository Initialisation"),
    ("stage-05-feature-branching.md",          "Task 5 — Feature Development with Branching"),
    ("stage-06-mvp-collaboration.md",          "Task 6 — MVP Completion and Git Collaboration"),
    ("stage-07-jenkins-ci.md",                 "Task 7 — Jenkins Installation and Continuous Integration Job"),
    ("stage-08-pipeline-as-code.md",           "Task 8 — Pipeline as Code and Server Deployment"),
    ("stage-09-selenium-tests.md",             "Task 9 — Selenium Test Design and Local Execution"),
    ("stage-10-continuous-testing.md",         "Task 10 — Continuous Testing in Jenkins"),
    ("stage-11-docker-lifecycle.md",           "Task 11 — Docker Image and Container Lifecycle"),
    ("stage-12-jenkins-docker-cd.md",          "Task 12 — Jenkins-Docker Continuous Deployment"),
    ("stage-13-configuration-management.md",   "Task 13 — Configuration Management Script"),
    ("stage-14-provisioning-reliability.md",   "Task 14 — Automated Provisioning and Reliability Validation"),
    ("stage-15-final-report.md",               "Task 15 — Final End-to-End Release, Documentation and Viva"),
]


def slug(text):
    s = text.lower()
    s = re.sub(r"[^\w\s-]", "", s)
    return re.sub(r"[\s_]+", "-", s).strip("-")


def demote(body):
    """Push every heading down one level so the combined file has one title.

    Fenced code blocks are skipped: a '#' at the start of a line inside a
    shell block is a comment, not a heading, and demoting it would corrupt
    the example.
    """
    out, in_fence, fence = [], False, ""
    for line in body.split("\n"):
        stripped = line.lstrip()
        if stripped.startswith("```") or stripped.startswith("~~~"):
            marker = stripped[:3]
            if not in_fence:
                in_fence, fence = True, marker
            elif marker == fence:
                in_fence, fence = False, ""
            out.append(line)
            continue
        if not in_fence and re.match(r"^#{1,5}\s", line):
            line = "#" + line
        out.append(line)
    return "\n".join(out)


def fix_links(body):
    """Rewrite intra-docs links so they resolve from the combined file."""
    # ../proofs/... -> proofs/...   (FINAL-REPORT.md lives in docs/)
    body = body.replace("](../proofs/", "](proofs/")
    body = body.replace("](../ansible/", "](../ansible/")
    return body


def main():
    today = datetime.date.today().isoformat()
    parts = []

    parts.append(f"""# Student Attendance Management Portal
## CI/CD Pipeline — Consolidated Project Report

**Repository:** <https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management>
**Author:** Swaroop Naik
**Compiled:** {today}

---

## About this document

This is the fifteen task reports of the project, combined into one
submission document. Each task also has its own standalone report under
[`docs/`](.) — this file concatenates them so the project can be read, or
submitted, as a single piece.

The reports are the ones written as each task was completed, not a summary
written afterwards. They record what was built, why each decision was made,
and what went wrong along the way, with the captured evidence for each
claim under [`proofs/`](../proofs).

### How the project is evidenced

Nothing in these reports is asserted without a command log, a build record,
a report or a screenshot behind it. Where something could not be done in
this environment, it is recorded as a limitation rather than quietly
omitted.

| Evidence type | Where |
|---|---|
| Build and test logs | `proofs/stage-NN/*.log` |
| Jenkins console output and stage timings | `proofs/stage-07`, `08`, `10`, `12`, `15` |
| Command sessions (Docker, Ansible, git) | `proofs/stage-06`, `11`, `13`, `14` |
| Screenshots | `proofs/stage-NN/*.png` |
| Test reports | `proofs/stage-05`, `09`, `10` |

---

## Contents

| # | Task | Deliverable |
|---|---|---|""")

    rows = [
        ("0", "Environment prerequisites", "Readiness check and pinned versions"),
        ("1", "Problem definition and scope", "Problem statement, stakeholders, objectives, constraints, frozen MVP"),
        ("2", "Agile planning and DevOps workflow", "Backlog, board, sprint plan, Definition of Done, workflow diagram"),
        ("3", "Requirements, architecture, setup", "SRS, use cases, architecture, data model, API list, local setup"),
        ("4", "Repository initialisation", "Repository, README, .gitignore, templates, branch policy, issues"),
        ("5", "Feature development with branching", "Feature 1, feature branch, pull request, review, merge"),
        ("6", "MVP completion and collaboration", "Functional MVP, resolved merge conflict, tagged version, backlog"),
        ("7", "Jenkins installation and CI job", "Jenkins job, build log, trigger evidence, archived artefact"),
        ("8", "Pipeline as code and deployment", "Jenkinsfile, pipeline run, deployed URL, parameter evidence"),
        ("9", "Selenium test design", "Test plan, scripts, local report, failure-screenshot mechanism"),
        ("10", "Continuous testing in Jenkins", "Published report, failed pipeline, fix commit, green rerun"),
        ("11", "Docker image and lifecycle", "Dockerfile, image details, command log, running container"),
        ("12", "Jenkins-Docker continuous deployment", "Versioned image, registry evidence, commit-to-container run"),
        ("13", "Configuration management script", "Configuration specification, Ansible playbook, first run log"),
        ("14", "Provisioning and reliability", "Provisioned node, idempotency, health check, rollback"),
        ("15", "Final release, documentation, viva", "End-to-end run, report, troubleshooting, limitations, viva pack"),
    ]
    for num, title, deliverable in rows:
        anchor = slug(SECTIONS[int(num)][1]) if num.isdigit() else ""
        parts.append(f"| {num} | [{title}](#{anchor}) | {deliverable} |")

    parts.append("""
---

## Project at a glance

| | |
|---|---|
| **Application** | Role-aware attendance system of record for an engineering college |
| **Stack** | Java 21, Spring Boot 3.5, Thymeleaf, Spring Data JPA, H2, Maven |
| **Packaging** | Executable WAR — deploys to Tomcat 10.1 *and* runs standalone |
| **CI/CD** | Jenkins LTS, configuration as code, declarative `Jenkinsfile` |
| **Testing** | 100 tests: 73 unit and slice, 7 integration, 20 Selenium journeys |
| **Containers** | Docker image, non-root, healthchecked; local `registry:2` |
| **Provisioning** | Ansible — four roles, idempotent, with health check and rollback |
| **Pipeline** | 11 stages, commit to healthy deployment in 178 s, no manual step |
| **Targets** | Tomcat, container, and an Ansible-provisioned node — all from one artefact |

---
""")

    for filename, title in SECTIONS:
        path = os.path.join(DOCS, filename)
        if not os.path.exists(path):
            print(f"  WARNING: {path} is missing")
            continue
        body = open(path, encoding="utf-8").read()
        # Drop the document's own H1; the contents entry supplies the title.
        body = re.sub(r"\A#\s+[^\n]*\n", "", body, count=1)
        body = fix_links(demote(body.strip()))
        parts.append(f"\n<a id=\"{slug(title)}\"></a>\n\n# {title}\n\n{body}\n\n---\n")
        print(f"  added {filename} ({len(body.splitlines())} lines)")

    parts.append("""
# End of report

Every claim in this document is backed by captured evidence under
[`proofs/`](../proofs). The per-task reports remain available individually
in [`docs/`](.).
""")

    combined = "\n".join(parts)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(combined)

    print(f"\nWrote {OUT}: {len(combined.splitlines())} lines, {len(combined) / 1024:.0f} KB")


if __name__ == "__main__":
    main()
