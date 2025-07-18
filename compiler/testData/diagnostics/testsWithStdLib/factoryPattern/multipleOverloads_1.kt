// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// LANGUAGE: +OverloadResolutionByLambdaReturnType
// ALLOW_KOTLIN_PACKAGE
// DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE -UNUSED_EXPRESSION -OPT_IN_USAGE -EXPERIMENTAL_UNSIGNED_LITERALS
// ISSUE: KT-11265

// FILE: OverloadResolutionByLambdaReturnType.kt

package kotlin

annotation class OverloadResolutionByLambdaReturnType

// FILE: main.kt

import kotlin.OverloadResolutionByLambdaReturnType

@OverloadResolutionByLambdaReturnType
fun <R> UByteArray.fooMap(t: (UByte) -> Iterable<R>): List<R> {
    TODO("ub.fm")
}

@OverloadResolutionByLambdaReturnType
fun <T, R> Iterable<T>.fooMap(t: (T) -> Iterable<R>): List<R> {
    TODO("i.fm(i)")
}

@JvmName("fooMapSeq")
fun <T, R> Iterable<T>.fooMap(t: (T) -> Sequence<R>): List<R> {
    TODO("i.fm(s)")
}

fun test() {
    val list = ubyteArrayOf(0u).fooMap { listOf(it) }
    takeUByteList(list)
}

fun takeUByteList(list: List<UByte>) {}

/* GENERATED_FIR_TAGS: annotationDeclaration, funWithExtensionReceiver, functionDeclaration, functionalType,
lambdaLiteral, localProperty, nullableType, propertyDeclaration, stringLiteral, typeParameter, unsignedLiteral */
