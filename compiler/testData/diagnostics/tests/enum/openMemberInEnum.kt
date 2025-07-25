// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
enum class EnumWithOpenMembers {
    E1 {
        override fun foo() = 1
        override val bar: String = "a"
    },

    E2 {
        <!OVERRIDING_FINAL_MEMBER!>override<!> fun f() = 3
        <!OVERRIDING_FINAL_MEMBER!>override<!> val b = 4
    };

    open fun foo() = 1
    open val bar: String = ""

    fun f() = 2
    val b = 3
}

/* GENERATED_FIR_TAGS: enumDeclaration, enumEntry, functionDeclaration, integerLiteral, propertyDeclaration,
stringLiteral */
