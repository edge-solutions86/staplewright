# staplewright

Key control and master key system design for the campus lock shop.

The shop keeps the keying systems for every building on campus. This is the
part of that work a computer can do: hold the manufacturer specifications,
progress a bitting array, pin the cylinders, keep track of who holds what,
and work out the damage when a key goes missing.

Nothing here talks to a lock. It talks about locks.

## Built with

- Java 21, built with Gradle (Groovy DSL) through the wrapper
- Spring Boot 3.5 (Web MVC, Validation) for the http service, with an OpenAPI document from springdoc
- Hibernate through JPA for the rows, Flyway for the schema, H2 as the database
- picocli for the `staplewright` command
- JUnit 5 and AssertJ for the tests, Spotless (palantir java format) and a JaCoCo line coverage gate of 80%

## Build, test, run

```
./gradlew build                     # format check, every test, the coverage gate, and the jar
./gradlew test --tests '*SystemsTest' # one slice while working on it
./gradlew spotlessApply             # format before committing
```

`./gradlew build` leaves the runnable jar at `build/libs/staplewright-1.0.0.jar`:

```
java -jar build/libs/staplewright-1.0.0.jar --version
java -jar build/libs/staplewright-1.0.0.jar build          # create the tables
java -jar build/libs/staplewright-1.0.0.jar demo           # or fill them with a worked example campus
java -jar build/libs/staplewright-1.0.0.jar check          # the self check, exit 1 on a fault
java -jar build/libs/staplewright-1.0.0.jar serve --port 8000
```

## The database

The database is named by `--database`, then by `STAPLEWRIGHT_DATABASE`, then defaults to an H2 file
`staplewright.mv.db` beside where the command runs (`jdbc:h2:./staplewright`). Any H2 URL works,
`jdbc:h2:mem:shop` included. The schema comes from the Flyway migrations in
`src/main/resources/db/migration`, one per schema revision; `staplewright build` applies them and
running it again changes nothing. The service checks the mapping against the schema when it first
opens a session and never builds tables itself.

## Settings

Every number the shop changes is read from the environment, named `STAPLEWRIGHT_` and the knob.
Values are whole numbers, true or false (`1/0`, `yes/no`, `on/off`), or for the stamping knobs a
marking by name (`symbol`, `blind`, `tag`, `nothing`). Anything unreadable is refused rather than
falling back to a default. `staplewright settings` and `GET /settings` print what is in force.

| variable | what it sets |
| --- | --- |
| `MAX_RUN`, `MIN_DISTINCT` | the cutting rules: longest run of one depth, fewest depths on a key |
| `DEPOSIT_CHANGE_CENTS`, `DEPOSIT_MASTER_CENTS` | the deposit on a change key and on a master |
| `REMINDER_DAYS`, `FIRST_NOTICE_DAYS`, `SECOND_NOTICE_DAYS`, `HOLD_DAYS`, `WRITE_OFF_DAYS` | the overdue notice ladder |
| `STRICT_REPIN`, `REKEY_EXTERIOR`, `REKEY_MASTERS`, `IGNORE_BELOW` | how far a rekey reaches after a loss |
| `LABOUR_CENTS_HOUR`, `BLANK_CENTS` | the rate card |
| `BOX_LOWEST_LEVEL`, `BOX_CHECK_DAYS`, `BOX_RESEAL_DAYS` | the sealed key box policy |
| `CONFIRM_DAYS`, `MASTER_HANDOVER` | how long a handover claim stands, and whether a master may change hands |
| `STAMP_CHANGE_KEYS`, `STAMP_MASTERS`, `STAMP_DND`, `BOW_CHARACTERS` | what goes on the brass |
| `QUARANTINE_YEARS`, `PROGRESSION_STEP` | how long a retired cut stays out, and the progression step |

## The command line

`staplewright --help` lists everything; every group takes `--help` as well. A refusal prints one
line, `refused: ` and the reason with the facts behind it, on standard error and exits 1. Arguments
that cannot be read exit 2.

| command | what it is for |
| --- | --- |
| `build`, `tables`, `settings` | create the schema, list the tables, print the settings |
| `demo` | build the schema and fill it with a worked example campus |
| `check` | run the self checks; `--quiet` sets the exit code only |
| `serve` | run the http service (`--host`, `--port`, 0 picks a free port) |
| `system` | open a keying system, draw and cut symbols, print the schedule, review it |
| `keyways` | keyway families, sections, and where the patents stand |
| `control` | control keys, interchangeable cores, and where a core could cross |
| `counter` | key requests, approvals, issues, returns and losses at the counter |
| `doors` | lockout calls, hardware surveys, access reviews and keyed alike sets |
| `boxes` | sealed key boxes and their seal history |
| `stamping` | what goes on the brass, and the blind code table |
| `stock` | the key blank ledger, counts, reorders and purchase orders |
| `rekey` | report a missing key, assess it, plan and book the work |
| `phasing` | spread a rekey across the academic year |
| `return` | the annual key control return |
| `export`, `import` | tables out as csv or json, and buildings, openings and keyholders in |
| `reports` | the index of every printed page, by module or by who asks for it |
| `release` | versions, what each one changed, and what an upgrade crosses |

Days are written `--on 2025-06-30` and moments `--at 2025-06-30T14:12:00`; both default to now.

## The http service

`staplewright serve` runs it; the OpenAPI document is at `/openapi.json` and the browsable one at
`/docs`. Bodies and answers are JSON with snake case fields. A refusal answers with the status the
refusal catalogue gives its code (`GET /refusals` lists them) and a body of its code, message and
facts; a request that cannot be read answers 422 with a `detail` list naming each problem. Report
routes take `?plain=true` to answer as text.

| routes | what they do |
| --- | --- |
| `GET /health`, `/health/checks`, `/health/report`, `/health/findings[/{name}]` | liveness, and the self check |
| `GET /refusals`, `GET /settings` | the refusal codes, and the settings in force |
| `/systems` | open a system, draw, cut and uncut symbols, read the schedule and its review |
| `/pinning/{system}/{group}` and `/pinning/draw` | stacks, the bench chart, the keys behind a group, drawing brass |
| `/requests`, `/keys/{tag}`, `/keyholders/{identifier}/keys` | the counter: requests, decisions, issues, returns, losses |
| `/handovers`, `/keys/{tag}/holder`, `/keyholders/{identifier}/holdings` | key handovers and who holds a key now |
| `/incidents` | report a missing key, assess it, plan the rekey, raise the order |
| `/lockouts`, `/surveys`, `/buildings/{building}/latest-survey` | lockout calls and hardware surveys |
| `/boxes` | sealed key boxes: install, walk, open, empty, reseal, withdraw, seal history |
| `/stamping` | blind codes, what goes on a key, stamping runs, the whole book behind one route |
| `/stock` | blanks, movements, counts, the reorder list and purchase orders |
| `/returns` | measure, file and withdraw the annual return, the sheet and the trend |
| `/reports` | the printed pages: schedules, statements, overdue, letters, campus, exposure, buildings |

## Layout

```
src/main/java/io/github/rkaushikdev/staplewright/
  (root)        refusals and their catalogue, the keyed registry, state checks, the version
  keys          bittings, key symbols, the keying schedule, the code machine, stamping, duplication
  spec          depth and space charts, cutting rules, keyway families, the catalogue, patents, cores
  progression   the progression plan, the key bitting array, capacity, keying groups, the schedule review
  pinning       pin segments, kits, chamber stacks, cylinder and core pinning
  campus        openings, the hardware register, coverage, access, zones, lockouts, surveys
  control       keyholders, the key ledger, deposits, notices, requests, reviews, key boxes, handovers
  rekey         incidents, scope, costing, work orders, construction keying, quarantine, phasing
  reports       every printed page, and the index of them
  settings      shop settings and reading them from the environment
  release       the release history
  health        the self checks over a live database
  persistence   the database, sessions, rows, mappers, loaders, repositories, numbering, the demo campus
  services      what the counter, the bench and the shop do, one session at a time
  api           the http service
  cli           the staplewright command
src/main/resources/db/migration   the schema, one migration per revision
src/test/java                     one test class per subject, and the shared fixtures under support/
```
