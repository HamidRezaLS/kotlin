// RUN_PIPELINE_TILL: BACKEND
data class Some(val x: Int, val y: String)

fun test(some: Some) {
    val other = some.copy(y = "123")
    val another = some.copy(x = 123)
    val same = some.copy()
    val different = some.copy(456, "456")
}

/* GENERATED_FIR_TAGS: classDeclaration, data, functionDeclaration, integerLiteral, localProperty, primaryConstructor,
propertyDeclaration, stringLiteral */
