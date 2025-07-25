// RUN_PIPELINE_TILL: BACKEND
// DISABLE_IR_VISIBILITY_CHECKS: JVM_IR
// ISSUE: KT-61065
// FILE: PrivateObjekt.kt

private object PrivateObjekt

// FILE: Main.kt

fun test(arg: Any?) {
    when (arg) {
        // K1: ok
        // K2: INVISIBLE_REFERENCE
        @Suppress("INVISIBLE_MEMBER", <!ERROR_SUPPRESSION!>"INVISIBLE_REFERENCE"<!>) PrivateObjekt -> Unit
    }

    @Suppress("INVISIBLE_MEMBER", <!ERROR_SUPPRESSION!>"INVISIBLE_REFERENCE"<!>) PrivateObjekt

    val it = @Suppress("INVISIBLE_MEMBER", <!ERROR_SUPPRESSION!>"INVISIBLE_REFERENCE"<!>) PrivateObjekt
}

/* GENERATED_FIR_TAGS: equalityExpression, functionDeclaration, localProperty, nullableType, objectDeclaration,
propertyDeclaration, stringLiteral, whenExpression, whenWithSubject */
