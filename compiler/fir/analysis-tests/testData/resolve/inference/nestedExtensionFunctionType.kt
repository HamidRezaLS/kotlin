// RUN_PIPELINE_TILL: BACKEND
// ISSUE: KT-39034

interface A

fun test_0(a: A, f: A.() -> Unit) {
    a.f()
}

fun test_1(a: A, ys: List<A.() -> Unit>) {
    for (y in ys) {
        a.y()
    }
}

fun test_2(a: A, vararg zs: A.() -> Unit) {
    for (z in zs) {
        a.z()
    }
}

/* GENERATED_FIR_TAGS: capturedType, forLoop, functionDeclaration, functionalType, interfaceDeclaration, localProperty,
outProjection, propertyDeclaration, typeWithExtension, vararg */
