// RUN_PIPELINE_TILL: BACKEND
class My(var x: Int) {
    operator fun invoke() = x

    fun foo() {}

    fun copy() = My(x)
}

fun testInvoke(): Int = My(13)()

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, integerLiteral, operator, primaryConstructor,
propertyDeclaration */
