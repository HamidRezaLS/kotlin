// TARGET_BACKEND: WASM

// RUN_THIRD_PARTY_OPTIMIZER
// WASM_DCE_EXPECTED_OUTPUT_SIZE: wasm  54_727
// WASM_DCE_EXPECTED_OUTPUT_SIZE: mjs    5_955
// WASM_OPT_EXPECTED_OUTPUT_SIZE:           76

// FILE: test.kt

@JsExport
fun add(a: Int, b: Int) = a + b

// FILE: entry.mjs
import { add } from "./index.mjs"

const r = add(2, 3);
if (r != 5) throw Error("Wrong result: " + r);
