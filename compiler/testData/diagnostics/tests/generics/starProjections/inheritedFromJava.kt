// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// FILE: p/Base.java

package p;

public class Base<T> {
    void foo(R<?> r) {}
}

// FILE: k.kt
package p

class R<T: R<T>>

class Derived: p.Base<String>()

/* GENERATED_FIR_TAGS: classDeclaration, javaType, typeConstraint, typeParameter */
