// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
fun foo0() : String = "noarg"

fun foo0(vararg t : Int) : String = "vararg"

fun test0() {
    foo0()
    foo0(1)
    val a = IntArray(0)
    foo0(*a)
}

/* GENERATED_FIR_TAGS: functionDeclaration, integerLiteral, localProperty, propertyDeclaration, stringLiteral, vararg */
