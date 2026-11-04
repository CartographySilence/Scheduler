Hello, this project was created to compliment my honours research project. It is an SMT-Based scheduler, the runs are contained in the repo, along with the code itself

If you ever intend to run this (I doubt anyone will) here is the requirements and how to build it 

## Requirements

- Java 17
- Maven 3.9+
- Rust and Cargo (only for the xmt-lib grounder)

Z3 4.13 is pulled in automatically through Maven (`tools.aqua:z3-turnkey`)
Z3 install is needed for the java side 

## Building

```bash
mvn compile
```
To build the external grounder
cd rust-grounder
cargo build --release --features static-link-z3

## Running
mvn compile exec:java

This will open up an interactive menu 

Basic is quantified SMT-LIB
built-in is my own naive grounder
xmt-lib is Carbonnelles xmt-lib grounder
restart-search is basic encoding with symmetry breaking
restart-search-hybrid is restart but uses a greedy method too
(would not recommend using the last two, for pure solving purposes xmt-lib is the best)

## Spec format

If you want to write your own
{
  "processors": 4,
  "tasks": [
    { "id": "ingest",   "duration": 5, "deadline": 12 },
    { "id": "validate", "duration": 3, "deadline": 20, "release": 2, "after": ["ingest"] }
  ]
}

release and after are both optional to include, after is used for precedence and release is the release time of the task

## Benchmarks
mvn compile exec:java -Dexec.mainClass=smtsched.bench.BenchRunner
