// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// OPT_IN: kotlin.contracts.ExperimentalContracts
// DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER

import kotlin.contracts.*

inline fun myRun(block: () -> Unit): Unit {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block()
}

inline fun unknownRun(block: () -> Unit) { block() }

fun foo() {
    val x: Int
    myRun {
        unknownRun { println("shouldn't change anything") }
        x = 42
    }
    println(x)
}

/* GENERATED_FIR_TAGS: assignment, contractCallsEffect, contracts, functionDeclaration, functionalType, inline,
integerLiteral, lambdaLiteral, localProperty, propertyDeclaration, stringLiteral */
