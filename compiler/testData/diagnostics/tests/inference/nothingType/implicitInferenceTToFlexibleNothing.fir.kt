// RUN_PIPELINE_TILL: FRONTEND
// DIAGNOSTICS: -UNUSED_VARIABLE -UNCHECKED_CAST -UNUSED_PARAMETER
// SKIP_TXT

import java.util.*

fun <T> foo (f: () -> List<T>): T = null as T

fun main() {
    val x = <!CANNOT_INFER_PARAMETER_TYPE!>foo<!> { Collections.<!CANNOT_INFER_PARAMETER_TYPE!>emptyList<!>() }
}

/* GENERATED_FIR_TAGS: asExpression, flexibleType, functionDeclaration, functionalType, javaFunction, lambdaLiteral,
localProperty, nullableType, propertyDeclaration, typeParameter */
