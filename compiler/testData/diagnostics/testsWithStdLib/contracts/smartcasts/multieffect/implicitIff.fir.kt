// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +AllowContractsForCustomFunctions +UseReturnsEffect
// OPT_IN: kotlin.contracts.ExperimentalContracts
// DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER

import kotlin.contracts.*

fun onlyTrue(b: Boolean): Boolean {
    contract {
        returns(true) implies (b)
    }
    return b
}

fun onlyFalse(b: Boolean): Boolean {
    contract {
        returns(false) implies (!b)
    }
    return b
}

fun trueAndFalse(b: Boolean): Boolean {
    contract {
        returns(true) implies (b)
        returns(false) implies (!b)
    }
    return b
}



// ==== actual tests ====

fun useOnlyTrueInTrueBranch(x: Any?) {
    if (onlyTrue(x is String)) {
        x.length
    }
    else {
        x.<!UNRESOLVED_REFERENCE!>length<!>
    }
}

fun useOnlyTrueInFalseBranch(x: Any?) {
    if (onlyTrue(x !is String)) {
        x.<!UNRESOLVED_REFERENCE!>length<!>
    }
    else {
        // No smartcast here, we don't know that condition is false here
        x.<!UNRESOLVED_REFERENCE!>length<!>
    }
}

fun useOnlyFalseInTrueBranch(x: Any?) {
    if (onlyFalse(x is String)) {
        // No smartcast here, we don't know that condition is true here
        x.<!UNRESOLVED_REFERENCE!>length<!>
    }
    else {
        x.<!UNRESOLVED_REFERENCE!>length<!>
    }
}

fun useOnlyFalseInFalseBranch(x: Any?) {
    if (onlyFalse(x !is String)) {
        x.<!UNRESOLVED_REFERENCE!>length<!>
    }
    else {
        x.length
    }
}

fun useTrueAndFalseInTrueBranch(x: Any?) {
    if (trueAndFalse(x is String)) {
        x.length
    }
    else {
        x.<!UNRESOLVED_REFERENCE!>length<!>
    }
}

fun useTrueAndFalseInFalseBranch(x: Any?) {
    if (trueAndFalse(x !is String)) {
        x.<!UNRESOLVED_REFERENCE!>length<!>
    }
    else {
        x.length
    }
}

/* GENERATED_FIR_TAGS: contractConditionalEffect, contracts, functionDeclaration, ifExpression, isExpression,
lambdaLiteral, nullableType, smartcast */
