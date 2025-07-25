// RUN_PIPELINE_TILL: BACKEND
// SKIP_TXT
// ISSUE: KT-45814

class Foo(val bar: String?)

fun test_1(foo: Foo?) {
    foo!!.bar.let {
        foo<!UNNECESSARY_SAFE_CALL!>?.<!>bar?.length // Unnecessary
        foo.bar?.length // Correct
        foo<!UNNECESSARY_SAFE_CALL!>?.<!>bar?.length // Unnecessary
    }
    foo.bar?.length
    foo<!UNNECESSARY_SAFE_CALL!>?.<!>bar?.length // Unnecessary
}

fun test_2(foo: Foo?) {
    foo!!.bar.let {
        foo.bar?.length // Correct
        foo<!UNNECESSARY_SAFE_CALL!>?.<!>bar?.length // Unnecessary
        Unit
    }
    foo.bar?.length
}

/* GENERATED_FIR_TAGS: checkNotNullCall, classDeclaration, functionDeclaration, lambdaLiteral, nullableType,
primaryConstructor, propertyDeclaration, safeCall, smartcast */
