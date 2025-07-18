// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: -ReportTypeVarianceConflictOnQualifierArguments

class Bar<K> {
    inner class Inner {
        inner class SuperInner
    }
}

abstract class Foo<in T> {
    abstract fun yuckyEventHandler(
        fn: Bar<<!TYPE_VARIANCE_CONFLICT_ERROR!>T<!>>.Inner.() -> Unit
    ): () -> Unit

    abstract fun second(fn: Bar<<!TYPE_VARIANCE_CONFLICT_ERROR!>T<!>>.Inner)

    abstract fun third(fn: Bar<<!TYPE_VARIANCE_CONFLICT_ERROR!>T<!>>.Inner.SuperInner)
}

abstract class Baz<out T> {
    abstract fun yuckyEventHandler(
        fn: Bar<<!TYPE_VARIANCE_CONFLICT_ERROR!>T<!>>.Inner.() -> Unit
    ): () -> Unit
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, functionalType, in, inner, nullableType, out,
typeParameter, typeWithExtension */
