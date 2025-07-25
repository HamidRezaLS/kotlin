// RUN_PIPELINE_TILL: FRONTEND
package a

interface Closeable {}
class C : Closeable {}

fun <T: Closeable, R> T.foo(block: (T)-> R) = block

fun <T: Closeable, R> T.foo(block: (T, T)-> R) = block

fun main() {
    C().foo { // no ambiguity here
        www ->
        <!UNRESOLVED_REFERENCE!>xs<!>
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, funWithExtensionReceiver, functionDeclaration, functionalType,
interfaceDeclaration, lambdaLiteral, nullableType, typeConstraint, typeParameter */
