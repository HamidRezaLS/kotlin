// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// MODULE: m1-common
// FILE: common.kt

expect open class Foo {
    fun existingFun()
    val existingParam: Int
}

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt

actual open class Foo {
    actual fun existingFun() {}
    actual val existingParam: Int = 904

    constructor(injectedConstructor: Int) {}
}

/* GENERATED_FIR_TAGS: actual, classDeclaration, expect, functionDeclaration, integerLiteral, propertyDeclaration,
secondaryConstructor */
