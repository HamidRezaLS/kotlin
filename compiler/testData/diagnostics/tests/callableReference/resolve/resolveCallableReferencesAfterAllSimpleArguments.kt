// RUN_PIPELINE_TILL: FRONTEND
// DIAGNOSTICS: -UNUSED_PARAMETER

interface A
interface B

fun fooB(b: B) {}

fun <T> bar(f: (T) -> Unit, e: T) {}
fun <T> baz(e: T, f: (T) -> Unit) {}

fun test(a: A, b: B) {
    // Note that diagnostic is always on callable references as they are resolved after simple arguments
    baz(a, <!TYPE_MISMATCH!>::fooB<!>)
    bar(<!TYPE_MISMATCH!>::fooB<!>, a)
}

/* GENERATED_FIR_TAGS: functionDeclaration, functionalType, interfaceDeclaration, nullableType, typeParameter */
