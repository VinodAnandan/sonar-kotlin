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

import org.jetbrains.kotlin.js.descriptorUtils.getKotlinTypeFqName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.sonar.check.Rule
import org.sonarsource.kotlin.api.checks.CallAbstractCheck
import org.sonarsource.kotlin.api.checks.FunMatcher
import org.sonarsource.kotlin.api.reporting.message
import org.sonarsource.kotlin.api.checks.determineType
import org.sonarsource.kotlin.api.frontend.KotlinFileContext

@org.sonarsource.kotlin.api.frontend.K1only
@Rule(key = "S6611")
class MapValuesShouldBeAccessedSafelyCheck : CallAbstractCheck() {

    private val issueMessage = message {
        code("Map")
        +" values should be accessed safely. Using the non-null assertion operator here can throw a NullPointerException."
    }

    override val functionsToVisit = listOf(
            FunMatcher(name = "get") {
                withDefiningSupertypes("kotlin.collections.Map", "kotlin.collections.MutableMap")
            }
    )

    override fun visitFunctionCall(callExpression: KtCallExpression, resolvedCall: ResolvedCall<*>, kotlinFileContext: KotlinFileContext) {
        val sibling = callExpression.getParentOfType<KtDotQualifiedExpression>(true)?.getNextSiblingIgnoringWhitespace()
        if (sibling is KtOperationReferenceExpression && sibling.operationSignTokenType == KtTokens.EXCLEXCL) {
            kotlinFileContext.reportIssue(callExpression.parent.parent, issueMessage)
        }
    }

    override fun visitClass(klass: KtClass, context: KotlinFileContext) {
        val arrayAccessExpressions = klass.collectDescendantsOfType<KtArrayAccessExpression> {
            checkSuperType(it, context.bindingContext)
        }

        arrayAccessExpressions.forEach {
            val sibling = it.getNextSiblingIgnoringWhitespace()
            if (sibling is KtOperationReferenceExpression && sibling.operationSignTokenType == KtTokens.EXCLEXCL)
                context.reportIssue(it.parent, issueMessage)
        }
    }

    private fun checkSuperType(arrayAccessExpression: KtArrayAccessExpression, bindingContext: BindingContext): Boolean {
        val type = arrayAccessExpression.arrayExpression.determineType(bindingContext) ?: return false
        if (checkIfSubtype(type)) return true
        return type.supertypes().any {
            checkIfSubtype(it)
        }
    }

    private fun checkIfSubtype(type: KotlinType) = type.getKotlinTypeFqName(false) == "kotlin.collections.Map"
            || type.getKotlinTypeFqName(false) == "kotlin.collections.MutableMap"

}
