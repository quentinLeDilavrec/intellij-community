/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.resolve;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 8/30/13
 */
public class GradleArtifactsContributor implements GradleMethodContextContributor {

  @Override
  public void process(@NotNull List<String> methodCallInfo,
                      @NotNull PsiScopeProcessor processor,
                      @NotNull ResolveState state,
                      @NotNull PsiElement place) {
    if (methodCallInfo.isEmpty() || methodCallInfo.size() < 2 || !"artifacts".equals(methodCallInfo.get(1))) {
      return;
    }
    final String text = place.getText();
    if (!methodCallInfo.contains(text) && place instanceof GrReferenceExpressionImpl) {
      GradleResolverUtil.addImplicitVariable(processor, state, (GrReferenceExpressionImpl)place, Object.class);
      return;
    }

    GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
    PsiClass contributorClass = psiManager.findClassWithCache(ArtifactHandler.class.getName(), place.getResolveScope());
    if (contributorClass != null) {
      contributorClass.processDeclarations(processor, state, null, place);
      // assuming that the method call is addition of an artifact to the given configuration.
      processAtrifactAddition(methodCallInfo.get(0), contributorClass, processor, state, place);
    }
  }

  private static void processAtrifactAddition(@NotNull String gradleConfigurationName,
                                              @NotNull PsiClass artifactHandlerClass,
                                              @NotNull PsiScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              @NotNull PsiElement place) {
    GrLightMethodBuilder builder = new GrLightMethodBuilder(place.getManager(), gradleConfigurationName);
    PsiClassType type = PsiType.getJavaLangObject(place.getManager(), place.getResolveScope());
    builder.addParameter(new GrLightParameter("artifactInfo", type, builder));
    processor.execute(builder, state);

    GrMethodCall call = PsiTreeUtil.getParentOfType(place, GrMethodCall.class);
    if (call == null) {
      return;
    }
    GrArgumentList args = call.getArgumentList();
    if (args == null) {
      return;
    }

    int argsCount = GradleResolverUtil.getGrMethodArumentsCount(args);
    argsCount++; // Configuration name is delivered as an argument.

    for (PsiMethod method : artifactHandlerClass.findMethodsByName("add", false)) {
      if (method.getParameterList().getParametersCount() == argsCount) {
        builder.setNavigationElement(method);
      }
    }
  }
}
