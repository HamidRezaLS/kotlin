// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// FILE: KotlinFile.kt

interface C : A, B

fun foo(c: C) {
    c.setSomething(c.getSomething() + 1)
    c.something++
}

// FILE: A.java
public interface A {
    int getSomething();
}

// FILE: B.java
public interface B {
    int getSomething();
    void setSomething(int value);
}

/* GENERATED_FIR_TAGS: additiveExpression, assignment, functionDeclaration, incrementDecrementExpression, integerLiteral,
interfaceDeclaration, javaFunction, javaType */
