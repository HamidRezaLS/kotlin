// RUN_PIPELINE_TILL: BACKEND
// ISSUE: KT-58751
class Result<out T>

interface Convert<T> {
    fun convert(str: String): Result<T & Any>
}

fun Convert<*>.cnv(value: String): Result<Any> = <!TYPE_MISMATCH!>convert(value)<!>

/* GENERATED_FIR_TAGS: classDeclaration, dnnType, funWithExtensionReceiver, functionDeclaration, interfaceDeclaration,
nullableType, out, starProjection, typeParameter */
