/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import dagger.Component;
import dagger.producers.ProductionComponent;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * A factory of {@link BindingGraph}s for use by <a href="https://kythe.io">Kythe</a>.
 *
 * <p>This is <b>not</b> intended to be used by any other APIs/processors and is not part of any
 * supported API except for Kythe.
 */
final class KytheBindingGraphFactory {
  private final ComponentDescriptor.Factory componentDescriptorFactory;
  private final BindingGraphFactory bindingGraphFactory;

  @Inject
  KytheBindingGraphFactory(Types types, Elements elements, CompilerOptions compilerOptions) {
    DaggerElements daggerElements = new DaggerElements(elements, types);
    DaggerTypes daggerTypes = new DaggerTypes(types, daggerElements);
    this.componentDescriptorFactory =
        createComponentDescriptorFactory(daggerElements, daggerTypes, compilerOptions);
    this.bindingGraphFactory =
        createBindingGraphFactory(daggerTypes, daggerElements, compilerOptions);
  }

  /**
   * Creates a {@link BindingGraph} for {@code type} if it is annotated with a component annotation,
   * otherwise returns {@link Optional#empty()}.
   */
  Optional<BindingGraph> create(TypeElement type) {
    if (MoreElements.isAnnotationPresent(type, Component.class)
        || MoreElements.isAnnotationPresent(type, ProductionComponent.class)) {
      return Optional.of(
          bindingGraphFactory.create(componentDescriptorFactory.forTypeElement(type)));
    }
    return Optional.empty();
  }

  /** Creates the {@link CompilerOptions} for use during {@link BindingGraph} construction. */
  // TODO(dpb): Use Dagger to inject this!
  static CompilerOptions createCompilerOptions() {
    return CompilerOptions.builder()
        .usesProducers(true)
        .writeProducerNameInToken(true)
        .nullableValidationKind(Diagnostic.Kind.NOTE)
        .privateMemberValidationKind(Diagnostic.Kind.NOTE)
        .staticMemberValidationKind(Diagnostic.Kind.NOTE)
        .ignorePrivateAndStaticInjectionForComponent(false)
        .scopeCycleValidationType(ValidationType.NONE)
        .warnIfInjectionFactoryNotGeneratedUpstream(false)
        .fastInit(false)
        .experimentalAndroidMode2(false)
        .aheadOfTimeSubcomponents(false)
        .moduleBindingValidationType(ValidationType.NONE)
        .moduleHasDifferentScopesDiagnosticKind(Diagnostic.Kind.NOTE)
        .build()
        .validate();
  }

  // TODO(dpb): Use Dagger to inject this!
  private static ComponentDescriptor.Factory createComponentDescriptorFactory(
      DaggerElements elements, DaggerTypes types, CompilerOptions compilerOptions) {
    KeyFactory keyFactory = new KeyFactory(types, elements);
    DependencyRequestFactory dependencyRequestFactory =
        new DependencyRequestFactory(keyFactory, types);
    BindingFactory provisionBindingFactory =
        new BindingFactory(types, elements, keyFactory, dependencyRequestFactory);
    MultibindingDeclaration.Factory multibindingDeclarationFactory =
        new MultibindingDeclaration.Factory(types, keyFactory);
    DelegateDeclaration.Factory bindingDelegateDeclarationFactory =
        new DelegateDeclaration.Factory(types, keyFactory, dependencyRequestFactory);
    SubcomponentDeclaration.Factory subcomponentDeclarationFactory =
        new SubcomponentDeclaration.Factory(keyFactory);
    OptionalBindingDeclaration.Factory optionalBindingDeclarationFactory =
        new OptionalBindingDeclaration.Factory(keyFactory);

    ModuleDescriptor.Factory moduleDescriptorFactory =
        new ModuleDescriptor.Factory(
            elements,
            provisionBindingFactory,
            multibindingDeclarationFactory,
            bindingDelegateDeclarationFactory,
            subcomponentDeclarationFactory,
            optionalBindingDeclarationFactory);
    return new ComponentDescriptor.Factory(
        elements, types, dependencyRequestFactory, moduleDescriptorFactory, compilerOptions);
  }

  // TODO(dpb): Use Dagger to inject this!
  private static BindingGraphFactory createBindingGraphFactory(
      DaggerTypes types, DaggerElements elements, CompilerOptions compilerOptions) {
    KeyFactory keyFactory = new KeyFactory(types, elements);

    BindingFactory bindingFactory =
        new BindingFactory(
            types, elements, keyFactory, new DependencyRequestFactory(keyFactory, types));

    InjectBindingRegistry injectBindingRegistry =
        new InjectBindingRegistryImpl(
            elements,
            types,
            new NullMessager(),
            new InjectValidator(
                types,
                elements,
                new DependencyRequestValidator(new MembersInjectionValidator()),
                compilerOptions),
            keyFactory,
            bindingFactory,
            compilerOptions);
    return new BindingGraphFactory(
        elements,
        injectBindingRegistry,
        keyFactory,
        bindingFactory,
        new ModuleDescriptor.Factory(
            elements,
            bindingFactory,
            new MultibindingDeclaration.Factory(types, keyFactory),
            new DelegateDeclaration.Factory(
                types, keyFactory, new DependencyRequestFactory(keyFactory, types)),
            new SubcomponentDeclaration.Factory(keyFactory),
            new OptionalBindingDeclaration.Factory(keyFactory)),
        compilerOptions);
  }

  private static class NullMessager implements Messager {
    @Override
    public void printMessage(Diagnostic.Kind kind, CharSequence charSequence) {}

    @Override
    public void printMessage(Diagnostic.Kind kind, CharSequence charSequence, Element element) {}

    @Override
    public void printMessage(
        Diagnostic.Kind kind,
        CharSequence charSequence,
        Element element,
        AnnotationMirror annotationMirror) {}

    @Override
    public void printMessage(
        Diagnostic.Kind kind,
        CharSequence charSequence,
        Element element,
        AnnotationMirror annotationMirror,
        AnnotationValue annotationValue) {}
  }
}
