// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Lazy value with ability to reset (and recompute) the value.
 * Thread-safe version: {@link AtomicClearableLazyValue}.
 */
public abstract class ClearableLazyValue<T> {
  @NotNull
  public static <T> ClearableLazyValue<T> create(@NotNull Computable<? extends T> computable) {
    return new ClearableLazyValue<T>() {
      @NotNull
      @Override
      protected T compute() {
        return computable.compute();
      }
    };
  }

  @NotNull
  public static <T> ClearableLazyValue<T> createAtomic(@NotNull Supplier<? extends T> computable) {
    return new AtomicClearableLazyValue<T>() {
      @NotNull
      @Override
      protected T compute() {
        return computable.get();
      }
    };
  }

  private T myValue;

  @NotNull
  protected abstract T compute();

  @NotNull
  public T getValue() {
    T result = myValue;
    if (result == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      result = compute();
      if (stamp.mayCacheNow()) {
        myValue = result;
      }
    }
    return result;
  }

  public void drop() {
    myValue = null;
  }
}
