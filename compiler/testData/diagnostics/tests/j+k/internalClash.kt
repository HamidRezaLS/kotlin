// RUN_PIPELINE_TILL: FRONTEND
// FILE: B.java

public class B extends A {}

// FILE: box.kt

open class A {
    internal open val a: String = "Fail"
}

class C : B() {
    internal val a: String = "OK"
}

fun box(): String {
    return C().a
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, javaType, override, propertyDeclaration, stringLiteral */
