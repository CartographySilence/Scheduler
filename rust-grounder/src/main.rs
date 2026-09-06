// Minimal subprocess bridge: reads an XMT-Lib script from stdin, grounds/executes
// it via the xmt-lib crate, and writes whatever it produces to stdout.
use std::io::{self, Read, Write};
use xmt_lib::solver::Solver;

fn main() {
    let mut script = String::new();
    io::stdin()
        .read_to_string(&mut script)
        .expect("failed to read script from stdin");

    let mut solver = Solver::new(None);
    let results = solver.parse_and_execute(&script);

    let stdout = io::stdout();
    let mut out = stdout.lock();
    for result in results {
        out.write_all(result.as_bytes()).expect("failed to write to stdout");
    }
}
