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

import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getParentCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.sonar.check.Rule
import org.sonarsource.kotlin.api.checks.AbstractCheck
import org.sonarsource.kotlin.api.checks.FunMatcher
import org.sonarsource.kotlin.api.frontend.KotlinFileContext

@org.sonarsource.kotlin.api.frontend.K1only
@Rule(key = "S5527")
class VerifiedServerHostnamesCheck : AbstractCheck() {

    companion object {
        val VERIFY_MATCHER = FunMatcher {
            definingSupertype = "javax.net.ssl.HostnameVerifier"
            name = "verify"
        }

        val HOSTNAME_VERIFIER_MATCHER = FunMatcher {
            qualifier = "okhttp3.OkHttpClient.Builder"
            name = "hostnameVerifier"
        }

        const val MESSAGE = "Enable server hostname verification on this SSL/TLS connection."
    }

    override fun visitNamedFunction(function: KtNamedFunction, kotlinFileContext: KotlinFileContext) {
        val (_, _, bindingContext) = kotlinFileContext
        if (VERIFY_MATCHER.matches(function, bindingContext)) {
            val listStatements = function.listStatements()
            if (listStatements.size == 1 && onlyReturnsTrue(listStatements[0], bindingContext)) {
                kotlinFileContext.reportIssue(function.nameIdentifier!!, MESSAGE)
            }
        }
    }

    override fun visitLambdaExpression(expression: KtLambdaExpression, kotlinFileContext: KotlinFileContext) {
        val (_, _, bindingContext) = kotlinFileContext
        expression.getParentCall(bindingContext)?.let {
            if (HOSTNAME_VERIFIER_MATCHER.matches(it, bindingContext)) {
                val listStatements = expression.bodyExpression?.statements
                if (listStatements?.size == 1 && listStatements[0].isTrueConstant(bindingContext)) {
                    kotlinFileContext.reportIssue(expression, MESSAGE)
                }
            }
        }
    }

    private fun onlyReturnsTrue(
        ktExpression: KtExpression,
        bindingContext: BindingContext,
    ): Boolean = when (ktExpression) {
        is KtReturnExpression ->
            ktExpression.returnedExpression?.isTrueConstant(bindingContext) ?: false
        else -> false
    }

    private fun KtExpression.isTrueConstant(
        bindingContext: BindingContext,
    ) = getType(bindingContext)?.let {
        bindingContext[BindingContext.COMPILE_TIME_VALUE, this]?.getValue(it) == true
    } ?: false
}
