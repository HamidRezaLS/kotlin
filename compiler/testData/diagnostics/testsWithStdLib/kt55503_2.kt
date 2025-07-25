// RUN_PIPELINE_TILL: BACKEND
// WITH_STDLIB
// FIR_DUMP

// FILE: First.kt

package sample.pack

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@kotlin.internal.HidesMembers
fun A.forEach() = "::A.forEach"

class A {
    fun B.forEach() = "A::B.forEach"
}

class B

// FILE: Second.kt

package sample

import sample.pack.*

fun box() {
    return with(A()) {
        with(B()) {
            // Both K1 & K2 resolve to A::B.check
            forEach()
        }
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, funWithExtensionReceiver, functionDeclaration, lambdaLiteral, stringLiteral */
