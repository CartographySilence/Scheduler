# Code Overview

How the current codebase maps onto the COS700 proposal ("SMT-based Task
Scheduling in Real-Time Operating Systems"). Written against the state of
`main` on 2026-09-04 (last commit `43af417`).

## Pipeline at a glance

```
JSON spec --> SpecParser --> Spec (validated model)
                                 |
                        +--------+--------+
                        |                 |
                    Encoder           TextEncoder
                (Z3 Java API calls)  (SMT-LIB text, quantified)
                        |                 |
                        |             Grounder (NoGround | RustGrounder)
                        |                 |
                        +------> Scheduler <------+
                                     |
                        Z3 Solver / Z3 Optimize
                                     |
                              Assignment list
                                     |
                                 GanttView (CLI render)
```

Two independent encoders exist side by side, not one. This is deliberate:
the proposal's ablation study needs a "hand-ground" baseline (pairwise
constraints emitted directly as Java/Z3 objects, an O(n²) loop) to compare
against the "quantified" path (compact `forall` assertions in SMT-LIB text
that would be expanded by a grounder before hitting Z3). `Encoder` is the
former, `TextEncoder` is the latter.

## Package-by-package

### `smtsched.spec` — the Parser
- `Spec` / `Spec.TaskDef`: immutable records for the task model (id,
  duration, deadline, `after` list), processor count.
- `SpecParser`: Jackson-based JSON deserialisation into a raw DTO, then
  validation (missing fields, non-positive durations/deadlines, duplicate
  ids, self-dependencies, dependencies on unknown tasks, duration >
  deadline). Matches the proposal's "validate before expensive translation"
  design.
- `SpecException`: single exception type for all spec-level failures.

### `smtsched.encode` — the Encoder
- `Encoder`: builds Z3 `IntExpr` decision variables (`start_T`, `proc_T`,
  and `late_T` for the lateness path) directly via the Java API.
  - `addCore`: bounds on `start`/`proc`, pairwise mutual-exclusion
    (`proc_i != proc_j \/ end_i <= start_j \/ end_j <= start_i`), and
    precedence (`end(dep) <= start(t)`).
  - `addHardDeadlines`: `end <= deadline` for the SAT/UNSAT path.
  - `addLateness`: `late_t >= 0`, `late_t >= end_t - deadline_t`, then
    minimises `Σ late_t` — used when the hard-deadline problem is UNSAT.
  - `addSymmetry`: two techniques, both beyond the strict wording of the
    proposal:
    - `addCoreSymmetry` — exactly the proposal's value-precedence scheme
      (`p1 = 0`, `pi <= 1 + max(p1..p_{i-1})`).
    - `addTaskSymmetry` — an additional technique (not in the proposal
      text, added in the `added task symmetry breaking` commit): tasks
      with no dependencies and no dependents are grouped by
      `(duration, deadline)`, and a fixed start-time order is pinned within
      each group of otherwise-interchangeable tasks.
- `TextEncoder`: emits actual SMT-LIB (`UFLIA` logic), with `start`/`proc`
  as uninterpreted functions over task indices and `forall`-quantified
  assertions for the domain, non-overlap, and precedence constraints —
  this is the encoding the grounding experiment is meant to operate on.
  Symmetry breaking is emitted the same way, just as ungrounded `forall`
  and grounded per-index assertions after the `n` decision.

### `smtsched.ground` — the (unfinished) grounding bridge
- `Grounder`: one-method interface, `String ground(String smtlib)`.
- `NoGround`: identity implementation — hands the quantified text straight
  to Z3's own E-matching.
- `RustGrounder`: **empty stub, no logic**. The proposal's Rust/xmt-lib
  subprocess bridge (generate xmt-lib script → Rust subprocess → grounded
  SMT-LIB back) is not implemented yet. `ModeComparison` has a literal
  `// TODO: xmt-lib` where this would plug in.

### `smtsched.schedule` — the Solver Interface
- `Scheduler.solve`: hard-deadline SAT/UNSAT check using `Encoder`
  (the object-API baseline).
- `Scheduler.solveText`: quantified text path — builds `TextEncoder`
  output, runs it through a `Grounder`, parses the (possibly still-
  quantified) result back into Z3 via `parseSMTLIB2String`, then checks.
- `Scheduler.solveLateness`: `Optimize`-based soft-deadline path, only
  invoked as a fallback when `solve` comes back UNSAT.
- Both hard paths run with a 100s Z3 timeout; `Outcome.TIMEOUT` is
  distinguished from `UNSCHEDULABLE` (Z3 `UNKNOWN` isn't proof of
  infeasibility).

### `smtsched.cli`
- `GanttView`: paged, ANSI-coloured terminal Gantt chart (green = on time,
  red = past deadline), with a legend and a lateness summary. Not
  mentioned in the proposal at all — a usability addition on top of the
  research pipeline.
- `ModeComparison`: runs a spec through hand-ground and quantified-text
  (NoGround only, since `RustGrounder` isn't built) with symmetry breaking
  on/off, and prints a timing table. This is the seed of the "ablation
  method" from the proposal's Experimental Evaluation section, but it's a
  console table, not the CSV/statistical pipeline described there.

### `smtsched.bench` — Test-suite generator + benchmark runner
- `Generator.random(nTasks, cores, density, tightness, seed)`: synthetic
  DAG generator — random durations, precedence edges sampled by density
  (capped at 3 preds/task, always pointing backward so the DAG is acyclic
  by construction), deadlines derived from an earliest-finish-time
  calculation scaled by a tightness factor. Covered by `GeneratorTest`
  (shape/validity + seed reproducibility).
- `Benchmark.run`: single (spec, symmetry) run against `Encoder`, records
  wall-clock solve time and assertion count.
- `BenchRunner`: sweeps `SIZES = {10,20,40,80}` × `CORES = {2,4,8}` ×
  `DENSITY = {0.1,0.3}` × 3 seeds, symmetry on/off, plus anything in
  `bench/specs/*.json` (currently one hand-written 4-task chain). Writes
  `bench/results.csv` and prints an off-vs-on timing summary.
  - This is noticeably smaller than the proposal's stated range (task
    count 10–500, processors 2–16) and only measures **solve time** and
    **assertion count** — proposal's other two metrics, **peak memory**
    and **clause count after grounding**, aren't collected anywhere yet
    (clause count in particular can't be until `RustGrounder` exists).
  - Only exercises the `Encoder` (hand-ground) path, not `TextEncoder` or
    grounding — the quantified/grounded comparison currently only lives in
    `ModeComparison`'s console table.

### `smtsched.Main`
Entry point. Reads a spec file if given (else the built-in `Samples.BIG`),
parses it, and either:
- runs `ModeComparison` if `--compare` is the last arg, or
- runs the normal path: try `solve` (hard deadlines) first; if
  `SCHEDULABLE`, print the Gantt chart directly; if `UNSCHEDULABLE`, fall
  back to `solveLateness` and print that instead; if `TIMEOUT`, report it.

### `smtsched.Samples`
Two built-in JSON specs (`PIPELINE`, a small 14-task example; `BIG`, an
~85-task multi-stage pipeline) used when no spec file is passed — handy
for manual runs without needing a file on disk.

## Proposal checklist — what's done vs. outstanding

| Proposal item | Status |
|---|---|
| JSON parser & data model | Done (`spec` package) |
| Naive encoder (baseline) | Done — both `Encoder` (API) and `TextEncoder` (SMT-LIB text) |
| Z3 Java integration | Done (`z3-turnkey` dependency, `Scheduler`) |
| Symmetry-breaking layer | Done, and extended beyond the proposal (core symmetry + task symmetry) |
| Rust / xmt-lib grounding bridge | **Not started** — `RustGrounder` is an empty class |
| Grounding bridge (full) | **Not started** — depends on the above |
| Test-suite generator | Done (`Generator`, `GeneratorTest`) |
| Benchmark runs | Partial — runner exists but smaller scale than proposed, and only the hand-ground path/two of three metrics are covered |
| Data analysis | Not started — no analysis beyond the console summary and raw CSV |
| Ablation harness (mode comparison) | Partial — `ModeComparison` covers hand-ground vs. quantified/ungrounded; grounded arm is the TODO |

## Biggest gap to close next

Everything the proposal frames as the *secondary* research question (does
grounding help, and at what scale) is blocked on `RustGrounder`, which is
currently a two-line empty file. `Grounder`/`NoGround`/`Scheduler.solveText`
are already wired up to take a real grounder as soon as one exists —
that's the natural next implementation step, followed by extending
`BenchRunner`/`Benchmark` to record memory and clause count and to sweep
the `TextEncoder` + grounded path, not just `Encoder`.
