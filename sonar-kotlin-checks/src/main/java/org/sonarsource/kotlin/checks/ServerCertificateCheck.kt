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
import org.jetbrains.kotlin.js.descriptorUtils.getKotlinTypeFqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.resolve.BindingContext
import org.sonar.check.Rule
import org.sonarsource.kotlin.api.checks.AbstractCheck
import org.sonarsource.kotlin.api.checks.FunMatcher
import org.sonarsource.kotlin.api.checks.determineType
import org.sonarsource.kotlin.api.frontend.KotlinFileContext


private const val CERTIFICATE_EXCEPTION = "java.security.cert.CertificateException"

private val funMatchers = listOf(
    FunMatcher {
        definingSupertype = "javax.net.ssl.X509TrustManager"
        withNames("checkClientTrusted", "checkServerTrusted")
    },
    FunMatcher {
        definingSupertype = "javax.net.ssl.X509ExtendedTrustManager"
        withNames("checkClientTrusted", "checkServerTrusted")
    })


@org.sonarsource.kotlin.api.frontend.K1only
@Rule(key = "S4830")
class ServerCertificateCheck : AbstractCheck() {
    override fun visitNamedFunction(function: KtNamedFunction, kotlinFileContext: KotlinFileContext) {
        val (_, _, bindingContext) = kotlinFileContext

        if (function.belongsToTrustManagerClass(bindingContext)
            && !function.callsCheckTrusted(bindingContext)
            && !function.throwsCertificateExceptionWithoutCatching(bindingContext)
        ) {
            kotlinFileContext.reportIssue(function.nameIdentifier ?: function,
                "Enable server certificate validation on this SSL/TLS connection.")
        }
    }

    private fun KtNamedFunction.belongsToTrustManagerClass(bindingContext: BindingContext): Boolean =
        funMatchers.any { it.matches(this, bindingContext) }

    /*
     * Returns true if a function contains a call to "checkClientTrusted" or "checkServerTrusted".
     */
    private fun KtNamedFunction.callsCheckTrusted(bindingContext: BindingContext): Boolean {
        val visitor = object : KtVisitorVoid() {
            private var foundCheckTrustedCall: Boolean = false

            override fun visitCallExpression(expression: KtCallExpression) {
                foundCheckTrustedCall = foundCheckTrustedCall || funMatchers.any { it.matches(expression, bindingContext) }
            }

            fun callsCheckTrusted(): Boolean = foundCheckTrustedCall
        }
        this.acceptRecursively(visitor)
        return visitor.callsCheckTrusted()
    }

    /*
     * Returns true only when the function throws a CertificateException without a catch against it.
     */
    private fun KtNamedFunction.throwsCertificateExceptionWithoutCatching(bindingContext: BindingContext): Boolean {
        val visitor = ThrowCatchVisitor(bindingContext)
        this.acceptRecursively(visitor)
        return visitor.throwsCertificateExceptionWithoutCatching()
    }

    private class ThrowCatchVisitor(private val bindingContext: BindingContext) : KtVisitorVoid() {
        private var throwFound: Boolean = false
        private var catchFound: Boolean = false

        override fun visitThrowExpression(expression: KtThrowExpression) {
            throwFound =
                throwFound || CERTIFICATE_EXCEPTION == expression.thrownExpression.determineType(bindingContext)?.getKotlinTypeFqName(false)
        }

        override fun visitCatchSection(catchClause: KtCatchClause) {
            catchFound =
                catchFound || CERTIFICATE_EXCEPTION == catchClause.catchParameter.determineType(bindingContext)?.getKotlinTypeFqName(false)
        }

        fun throwsCertificateExceptionWithoutCatching(): Boolean {
            return throwFound && !catchFound
        }
    }

    private fun PsiElement.acceptRecursively(visitor: KtVisitorVoid) {
        this.accept(visitor)
        for (child in this.children) {
            child.acceptRecursively(visitor)
        }
    }
}
