// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// FILE: J.java

import org.jetbrains.annotations.*;
import java.util.*;

public class J {
    @Nullable
    public List<String> n() { return null; }
}

// FILE: k.kt

fun list(j: J): Any {
    val a = j.n()!!

    a<!UNNECESSARY_SAFE_CALL!>?.<!>get(0)
    if (<!SENSELESS_COMPARISON!>a == null<!>) {}
    a<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>

    a.get(0)
    return a
}

/* GENERATED_FIR_TAGS: checkNotNullCall, equalityExpression, flexibleType, functionDeclaration, ifExpression,
integerLiteral, javaFunction, javaType, localProperty, nullableType, propertyDeclaration, safeCall */
