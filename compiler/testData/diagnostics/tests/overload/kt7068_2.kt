// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// DIAGNOSTICS: -UNUSED_PARAMETER

fun withLambda(block : Int.(String) -> Unit) {
}

fun withLambda(block : Int.(String, String) -> Unit) {
}

fun test() {
    withLambda { r ->
        r.length
    }

    withLambda { x, y ->
        x.length + y.length
    }
}

/* GENERATED_FIR_TAGS: additiveExpression, functionDeclaration, functionalType, lambdaLiteral, typeWithExtension */
