// RUN_PIPELINE_TILL: FRONTEND
// CHECK_TYPE
// WITH_EXTRA_CHECKERS

import <!PLATFORM_CLASS_MAPPED_TO_KOTLIN!>java.lang.Comparable<!> as Comparable

fun f(c: <!PLATFORM_CLASS_MAPPED_TO_KOTLIN!>Comparable<*><!>) {
    checkSubtype<kotlin.Comparable<*>>(<!TYPE_MISMATCH!>c<!>)
    checkSubtype<<!PLATFORM_CLASS_MAPPED_TO_KOTLIN!>java.lang.Comparable<*><!>>(c)
}

/* GENERATED_FIR_TAGS: classDeclaration, funWithExtensionReceiver, functionDeclaration, functionalType, infix,
nullableType, starProjection, typeParameter, typeWithExtension */
