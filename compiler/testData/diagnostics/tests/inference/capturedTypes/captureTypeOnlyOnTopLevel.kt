// RUN_PIPELINE_TILL: FRONTEND
// DIAGNOSTICS: -UNUSED_VARIABLE

fun <T> foo(array: Array<Array<T>>): Array<Array<T>> = array

fun test(array: Array<Array<out Int>>) {
    foo(<!TYPE_MISMATCH!>array<!>)

    val f: Array<out Array<out Int>> = foo(<!TYPE_MISMATCH!>array<!>)
}

/* GENERATED_FIR_TAGS: functionDeclaration, localProperty, nullableType, outProjection, propertyDeclaration,
typeParameter */
