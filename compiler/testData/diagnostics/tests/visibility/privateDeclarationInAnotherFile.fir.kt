// RUN_PIPELINE_TILL: FRONTEND
// FILE: 1.kt

private class Private {
    class Public
}

// FILE: 2.kt

import <!INVISIBLE_REFERENCE!>Private<!>.Public

private fun test_1(x: <!INVISIBLE_REFERENCE!>Private<!>.Public, y: <!INVISIBLE_REFERENCE!>Public<!>) {

}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, nestedClass */
