/*
 * SonarSource Kotlin
 * Copyright (C) 2018-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.kotlin.checks

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.sonar.check.Rule
import org.sonar.check.RuleProperty
import org.sonarsource.kotlin.api.checks.AbstractCheck
import org.sonarsource.kotlin.api.checks.asString
import org.sonarsource.kotlin.api.reporting.SecondaryLocation
import org.sonarsource.kotlin.api.reporting.KotlinTextRanges.textRange
import org.sonarsource.kotlin.api.frontend.KotlinFileContext

@Rule(key = "S1192")
class StringLiteralDuplicatedCheck : AbstractCheck() {

    companion object {
        private const val DEFAULT_THRESHOLD = 3
        private const val MINIMAL_LITERAL_LENGTH = 5
        private val NO_SEPARATOR_REGEXP = Regex("\\w++")
    }

    @RuleProperty(
        key = "threshold",
        description = "Number of times a literal must be duplicated to trigger an issue",
        defaultValue = "" + DEFAULT_THRESHOLD
    )
    var threshold = DEFAULT_THRESHOLD

    private fun check(
        context: KotlinFileContext,
        occurrencesMap: Map<String, List<KtStringTemplateExpression>>,
    ) {
        for ((_, occurrences) in occurrencesMap) {
            val size = occurrences.size
            if (size >= threshold) {
                val first = occurrences[0]
                context.reportIssue(
                    first,
                    """Define a constant instead of duplicating this literal "${first.asString()}" $size times.""",
                    secondaryLocations = occurrences.asSequence()
                        .drop(1)
                        .map { SecondaryLocation(context.textRange(it), "Duplication") }
                        .toList(),
                    gap = size - 1.0,
                )
            }
        }
    }

    override fun visitKtFile(file: KtFile, context: KotlinFileContext) {
        val occurrences = collectStringTemplatesNotInAnnotations(file)
            .map { it to it.asString() }
            .filter { (_, text) -> text.length > MINIMAL_LITERAL_LENGTH && !NO_SEPARATOR_REGEXP.matches(text) }
            .groupBy({ (_, text) -> text }) { it.first }
        check(context, occurrences)
    }

    private fun collectStringTemplatesNotInAnnotations(node: PsiElement): Sequence<KtStringTemplateExpression> =
        when {
            node is KtStringTemplateExpression && !node.hasInterpolation() -> sequenceOf(node)
            node is KtAnnotationEntry -> emptySequence()
            else -> node.children.asSequence().flatMap { collectStringTemplatesNotInAnnotations(it) }
        }
}
