// RUN_PIPELINE_TILL: FRONTEND
fun <T> getT(): T = null!!

class Test<in I> {
    private fun foo() : I = getT()

    fun apply(i: I) {}

    init {
        foo()
        this.foo()
    }

    fun test() {
        apply(foo())
        apply(this.foo())
        with(Test<I>()) {
            apply(foo()) // K1: this@Test.foo, K2: this@with.foo, see KT-55446
            apply(this.<!INVISIBLE_MEMBER("foo; private/*private to this*/; 'Test'")!>foo<!>())
            apply(this@with.<!INVISIBLE_MEMBER("foo; private/*private to this*/; 'Test'")!>foo<!>())
            apply(this@Test.foo())
        }
    }

    fun <I> test(t: Test<I>) {
        t.apply(t.<!INVISIBLE_MEMBER("foo; private/*private to this*/; 'Test'")!>foo<!>())
    }

    companion object {
        fun <I> test(t: Test<I>) {
            t.apply(t.<!INVISIBLE_MEMBER("foo; private/*private to this*/; 'Test'")!>foo<!>())
        }
    }
}

fun <I> test(t: Test<I>) {
    t.apply(t.<!INVISIBLE_MEMBER("foo; private/*private to this*/; 'Test'")!>foo<!>())
}

/* GENERATED_FIR_TAGS: checkNotNullCall, classDeclaration, companionObject, functionDeclaration, in, init, lambdaLiteral,
nullableType, objectDeclaration, thisExpression, typeParameter */
