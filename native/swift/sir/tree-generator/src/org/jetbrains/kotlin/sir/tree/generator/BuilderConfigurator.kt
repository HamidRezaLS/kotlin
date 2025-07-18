/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.sir.tree.generator

import org.jetbrains.kotlin.sir.tree.generator.config.AbstractSwiftIrTreeBuilderConfigurator
import org.jetbrains.kotlin.sir.tree.generator.model.Element

class BuilderConfigurator(model: Model) : AbstractSwiftIrTreeBuilderConfigurator(model) {

    override fun configureBuilders() = with(SwiftIrTree) {
        configureAllLeafBuilders {
            withCopy()
        }

        configureFieldInAllLeafBuilders("origin") {
            default(it, "SirOrigin.Unknown")
        }

        configureFieldInAllLeafBuilders("visibility") {
            default(it, "SirVisibility.PUBLIC")
        }

        configureFieldInAllLeafBuilders("isOverride") {
            default(it, "false")
        }

        configureFieldInAllLeafBuilders("isInstance") {
            default(it, "true")
        }

        configureFieldInAllLeafBuilders("modality") {
            default(it, "SirModality.UNSPECIFIED")
        }

        configureFieldInAllLeafBuilders("isConvenience") {
            default(it, "false")
        }

        configureFieldInAllLeafBuilders("isRequired") {
            default(it, "false")
        }

        configureFieldInAllLeafBuilders("errorType") {
            default(it, "SirType.never")
        }

        configureFieldInAllLeafBuilders("bridges") {
            default(it, "emptyList()")
        }

        builder(setter) {
            default("parameterName", "\"newValue\"")
        }
    }
}
