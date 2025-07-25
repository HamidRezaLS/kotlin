// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
<!NO_EXPLICIT_VISIBILITY_IN_API_MODE!>fun run<!>(b: () -> Unit) {}

<!NO_EXPLICIT_VISIBILITY_IN_API_MODE!>fun test<!>() {
    run {

    }

    fun localFun() {}
    localFun()
    run(::localFun)

    val localFun2 = fun() {}
    run(localFun2)

    val lambda = {}
    run(lambda)
}

/* GENERATED_FIR_TAGS: anonymousFunction, callableReference, functionDeclaration, functionalType, lambdaLiteral,
localFunction, localProperty, propertyDeclaration */
