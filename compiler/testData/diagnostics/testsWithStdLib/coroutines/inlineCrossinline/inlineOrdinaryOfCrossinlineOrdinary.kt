// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +ForbidExtensionCallsOnInlineFunctionalParameters
// DIAGNOSTICS: -UNUSED_VARIABLE
// WITH_COROUTINES
// SKIP_TXT
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import helpers.*

// Function is NOT suspend
// parameter is crossinline
// parameter is NOT suspend
// Block is allowed to be called inside the body of owner inline function
// Block is allowed to be called from nested classes/lambdas (as common crossinlines)
// It is NOT possible to call startCoroutine on the parameter
// suspend calls NOT possible inside lambda matching to the parameter

inline fun test(crossinline c: () -> Unit) {
    c()
    val o = object: Runnable {
        override fun run() {
            c()
        }
    }
    val l = { c() }
    c.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>startCoroutine<!>(EmptyContinuation)
}

suspend fun calculate() = "OK"

fun box() {
    test {
        <!ILLEGAL_SUSPEND_FUNCTION_CALL!>calculate<!>()
    }
}

/* GENERATED_FIR_TAGS: anonymousObjectExpression, assignment, classDeclaration, companionObject, crossinline,
functionDeclaration, functionalType, inline, lambdaLiteral, localProperty, nullableType, objectDeclaration, override,
primaryConstructor, propertyDeclaration, safeCall, stringLiteral, suspend, thisExpression, typeParameter */
