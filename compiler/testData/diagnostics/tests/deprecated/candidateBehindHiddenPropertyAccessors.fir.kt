// RUN_PIPELINE_TILL: BACKEND
class C {
    val v1: String
        @Deprecated("", level = DeprecationLevel.HIDDEN)
        get() = ""

    @Deprecated("", level = DeprecationLevel.HIDDEN)
    val v2 = ""

    var v3: String
        @Deprecated("", level = DeprecationLevel.HIDDEN)
        get() = ""
        set(value) {}

    var v4: String
        get() = ""
        @Deprecated("", level = DeprecationLevel.HIDDEN)
        set(value) {
        }

    var v5: String
        @Deprecated("", level = DeprecationLevel.HIDDEN)
        get() = ""
        @Deprecated("", level = DeprecationLevel.HIDDEN)
        set(value) {
        }

    @Deprecated("", level = DeprecationLevel.HIDDEN)
    var v6: String
        get() = ""
        set(value) {}
}

val v1: String = ""
val v2: String = ""
var v3: String = ""
var v4: String = ""
var v5: String = ""
var v6: String = ""

fun test(c: C) {
    with (c) {
        v1  // DEPRECATION_ERROR in FE 1.0, see KT-48799
        v2
        v3  // DEPRECATION_ERROR in FE 1.0, see KT-48799
        v3 = ""
        v4
        v4 = ""  // DEPRECATION_ERROR in FE 1.0, see KT-48799
        v5  // DEPRECATION_ERROR in FE 1.0, see KT-48799
        v5 = ""  // DEPRECATION_ERROR in FE 1.0, see KT-48799
        v6
        v6 = ""
    }
}

/* GENERATED_FIR_TAGS: assignment, classDeclaration, functionDeclaration, getter, lambdaLiteral, propertyDeclaration,
setter, stringLiteral */
