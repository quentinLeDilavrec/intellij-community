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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 8/29/13
 */
public class GradleSourceSetsContributor implements GradleMethodContextContributor {
  static final String SOURCE_SETS = "sourceSets";
  private static final String CONFIGURE_CLOSURE_METHOD = "configure";
  private static final int SOURCE_SET_CONTAINER_LEVEL = 1;
  private static final int SOURCE_SET_LEVEL = 2;
  private static final int SOURCE_DIRECTORY_LEVEL = 3;
  private static final int SOURCE_DIRECTORY_CLOSURE_LEVEL = 4;

  @Override
  public void process(@NotNull List<String> methodCallInfo,
                      @NotNull PsiScopeProcessor processor,
                      @NotNull ResolveState state,
                      @NotNull PsiElement place) {
    if (methodCallInfo.isEmpty()) {
      return;
    }
    if (methodCallInfo.size() > SOURCE_DIRECTORY_CLOSURE_LEVEL) {
      String method = ContainerUtil.getLastItem(methodCallInfo);
      if (method != null && !StringUtil.startsWith(method, SOURCE_SETS)) {
        return;
      }
    }

    final String methodCall = methodCallInfo.get(0);
    Class configureClosureClazz = null;
    Class contributorClass = null;
    if (methodCallInfo.size() == SOURCE_SET_CONTAINER_LEVEL) {
      configureClosureClazz = SourceSetContainer.class;
      if (place instanceof GrReferenceExpressionImpl) {
        Class varClazz = StringUtil.startsWith(methodCall, SOURCE_SETS + '.') ? SourceSetContainer.class : SourceSet.class;
        GradleResolverUtil.addImplicitVariable(processor, state, (GrReferenceExpressionImpl)place, varClazz);
      }
      else {
        contributorClass = SourceSetContainer.class;
      }
    }
    else if (methodCallInfo.size() == SOURCE_SET_LEVEL) {
      configureClosureClazz = SourceSet.class;
      contributorClass = SourceSet.class;
    }
    else if (methodCallInfo.size() == SOURCE_DIRECTORY_LEVEL) {
      configureClosureClazz = SourceDirectorySet.class;
      contributorClass = SourceDirectorySet.class;
    }
    else if (methodCallInfo.size() == SOURCE_DIRECTORY_CLOSURE_LEVEL) {
      contributorClass = SourceDirectorySet.class;
    }

    if (configureClosureClazz != null) {
      GrLightMethodBuilder methodWithClosure =
        GradleResolverUtil.createMethodWithClosure(CONFIGURE_CLOSURE_METHOD, place, configureClosureClazz);
      processor.execute(methodWithClosure, state);
    }
    if (contributorClass != null) {
      GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
      PsiClass psiClass = psiManager.findClassWithCache(contributorClass.getName(), place.getResolveScope());
      if (psiClass != null) {
        psiClass.processDeclarations(processor, state, null, place);
      }
    }
  }
}
