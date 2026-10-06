/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.benchmarks.jmh

import kotlinx.benchmark.*
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class SequenceFilterIndexedBenchmark {

    @Param("100", "10000", "100000")
    var size: Int = 0

    private lateinit var data: List<Int>

    @Setup
    fun setUp() {
        data = (0 until size).toList()
    }

    // Baseline: simulates the legacy 3-layer iterator wrapper sequence implementation:
    // TransformingSequence(FilteringSequence(IndexingSequence(this), ...))
    private fun <T> Sequence<T>.filterIndexedBaseline(predicate: (index: Int, T) -> Boolean): Sequence<T> {
        return this.withIndex().filter { predicate(it.index, it.value) }.map { it.value }
    }

    @Benchmark
    fun baselineToList(bh: Blackhole) {
        val result = data.asSequence().filterIndexedBaseline { index, _ -> index % 2 == 0 }.toList()
        bh.consume(result)
    }

    @Benchmark
    fun optimizedToList(bh: Blackhole) {
        val result = data.asSequence().filterIndexed { index, _ -> index % 2 == 0 }.toList()
        bh.consume(result)
    }

    @Benchmark
    fun baselineConsume(bh: Blackhole) {
        for (item in data.asSequence().filterIndexedBaseline { index, _ -> index % 2 == 0 }) {
            bh.consume(item)
        }
    }

    @Benchmark
    fun optimizedConsume(bh: Blackhole) {
        for (item in data.asSequence().filterIndexed { index, _ -> index % 2 == 0 }) {
            bh.consume(item)
        }
    }
}
