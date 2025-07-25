// RUN_PIPELINE_TILL: FRONTEND

inline fun <T> tryLambdas(lamb : () -> T) : T{
    return lamb.invoke()
}
fun main() {
    tryLambdas<String> {
        <!RETURN_TYPE_MISMATCH!>return@tryLambdas<!>
    }
}

/* GENERATED_FIR_TAGS: functionDeclaration, functionalType, inline, lambdaLiteral, nullableType, typeParameter */
