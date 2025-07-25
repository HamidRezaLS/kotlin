// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// CHECK_TYPE

import kotlin.reflect.*

class A

fun A.foo() {}
fun A.bar(x: Int) {}
fun A.baz() = "OK"

fun main() {
    val x = A::foo
    val y = A::bar
    val z = A::baz

    checkSubtype<KFunction1<A, Unit>>(x)
    checkSubtype<KFunction2<A, Int, Unit>>(y)
    checkSubtype<KFunction1<A, String>>(z)

    checkSubtype<KFunction<Unit>>(x)
    checkSubtype<KFunction<Unit>>(y)
    checkSubtype<KFunction<String>>(z)
}

/* GENERATED_FIR_TAGS: callableReference, classDeclaration, funWithExtensionReceiver, functionDeclaration,
functionalType, infix, localProperty, nullableType, propertyDeclaration, stringLiteral, typeParameter, typeWithExtension */
