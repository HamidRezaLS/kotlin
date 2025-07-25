// RUN_PIPELINE_TILL: FRONTEND
interface Foo
class FooImpl : Foo
class FooBarImpl : Foo

fun <T : Foo> foo(t: T) = t


fun main(fooImpl: FooImpl, fooBarImpl: FooBarImpl) {
    val a = foo<FooImpl>(<!ARGUMENT_TYPE_MISMATCH!>fooBarImpl<!>)
    val b = foo<Foo>(fooImpl)
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, interfaceDeclaration, localProperty, propertyDeclaration,
typeConstraint, typeParameter */
