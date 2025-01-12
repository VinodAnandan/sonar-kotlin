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

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.resolve.calls.util.getFirstArgumentExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.sonar.check.Rule
import org.sonarsource.kotlin.api.checks.CallAbstractCheck
import org.sonarsource.kotlin.api.checks.FunMatcher
import org.sonarsource.kotlin.api.checks.predictRuntimeBooleanValue
import org.sonarsource.kotlin.api.frontend.KotlinFileContext

private const val MESSAGE = "Make sure this debug feature is deactivated before delivering the code in production."

@org.sonarsource.kotlin.api.frontend.K1only
@Rule(key = "S4507")
class DebugFeatureEnabledCheck : CallAbstractCheck() {

    override val functionsToVisit = listOf(
        FunMatcher(definingSupertype = "android.webkit.WebView", name = "setWebContentsDebuggingEnabled")
        { withArguments("kotlin.Boolean") },
        FunMatcher(definingSupertype = "android.webkit.WebViewFactoryProvider.Statics", name = "setWebContentsDebuggingEnabled")
        { withArguments("kotlin.Boolean") },
    )

    override fun visitFunctionCall(callExpression: KtCallExpression, resolvedCall: ResolvedCall<*>, kotlinFileContext: KotlinFileContext) {
        if (resolvedCall.getFirstArgumentExpression()?.predictRuntimeBooleanValue(kotlinFileContext.bindingContext) == true) {
            kotlinFileContext.reportIssue(callExpression, MESSAGE)
        }
    }

}
