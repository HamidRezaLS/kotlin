// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
class Inv<T>

inline operator fun <reified T> Inv<T>.invoke() {}

operator fun <K> Inv<K>.get(i: Int): Inv<K> = this

fun <K> test(a: Inv<K>) {
    <!TYPE_PARAMETER_AS_REIFIED!>a[1]()<!>
}

/* GENERATED_FIR_TAGS: classDeclaration, funWithExtensionReceiver, functionDeclaration, inline, integerLiteral,
nullableType, operator, reified, thisExpression, typeParameter */
