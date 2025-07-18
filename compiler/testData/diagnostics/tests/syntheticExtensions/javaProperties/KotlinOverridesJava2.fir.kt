// RUN_PIPELINE_TILL: FRONTEND
// FILE: KotlinFile.kt
abstract class KotlinClass : JavaInterface1, JavaInterface2 {
    override fun getSomething(): String = ""
}

fun foo(k: KotlinClass) {
    useString(k.getSomething())
    useString(k.something)
    if (<!SENSELESS_COMPARISON!>k.something == null<!>) return

    k.setSomething(1)
    k.<!VAL_REASSIGNMENT!>something<!> = <!ASSIGNMENT_TYPE_MISMATCH!>1<!>
}

fun useString(i: String) {}

// FILE: JavaInterface1.java
public interface JavaInterface1 {
    String getSomething();
}

// FILE: JavaInterface2.java
public interface JavaInterface2 {
    void setSomething(int value);
}

/* GENERATED_FIR_TAGS: assignment, classDeclaration, equalityExpression, functionDeclaration, ifExpression,
integerLiteral, javaFunction, javaProperty, javaType, override, stringLiteral */
