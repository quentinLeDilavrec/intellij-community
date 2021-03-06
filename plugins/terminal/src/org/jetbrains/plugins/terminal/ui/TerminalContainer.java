// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalView;

import javax.swing.*;
import java.awt.*;

public class TerminalContainer {

  private static final Logger LOG = Logger.getInstance(TerminalContainer.class);

  private final Content myContent;
  private final JBTerminalWidget myTerminalWidget;
  private final Project myProject;
  private final TerminalView myTerminalView;
  private JPanel myPanel;

  public TerminalContainer(@NotNull Project project,
                           @NotNull Content content,
                           @NotNull JBTerminalWidget terminalWidget,
                           @NotNull TerminalView terminalView) {
    myProject = project;
    myContent = content;
    myTerminalWidget = terminalWidget;
    myTerminalView = terminalView;
    myPanel = createPanel(terminalWidget);
    terminalWidget.addListener(widget -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        onSessionClosed();
      }, myProject.getDisposed());
    });
    terminalView.register(this);
  }

  public @NotNull JBTerminalWidget getTerminalWidget() {
    return myTerminalWidget;
  }

  public @NotNull Content getContent() {
    return myContent;
  }

  private static @NotNull JPanel createPanel(@NotNull JBTerminalWidget terminalWidget ) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(null);
    panel.setFocusable(false);
    panel.add(terminalWidget.getComponent(), BorderLayout.CENTER);
    return panel;
  }

  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  public void split(boolean vertically, @NotNull JBTerminalWidget newTerminalWidget) {
    JPanel parent = myPanel;
    parent.remove(myTerminalWidget.getComponent());

    myPanel = createPanel(myTerminalWidget);

    Splitter splitter = new OnePixelSplitter(vertically, 0.5f, 0.1f, 0.9f);
    splitter.setDividerWidth(JBUI.scale(1));
    splitter.setFirstComponent(myPanel);
    TerminalContainer newContainer = new TerminalContainer(myProject, myContent, newTerminalWidget, myTerminalView);
    splitter.setSecondComponent(newContainer.getComponent());

    parent.add(splitter, BorderLayout.CENTER);
    parent.revalidate();
  }

  private void onSessionClosed() {
    Container parent = myPanel.getParent();
    if (parent instanceof Splitter) {
      boolean hasFocus = UIUtil.isFocusAncestor(myPanel);
      Splitter splitter = (Splitter)parent;
      parent = parent.getParent();
      JComponent otherComponent = myPanel.equals(splitter.getFirstComponent()) ? splitter.getSecondComponent()
                                                                               : splitter.getFirstComponent();
      Component realComponent = unwrapComponent(otherComponent);
      if (realComponent instanceof JBTerminalWidget) {
        TerminalContainer otherContainer = myTerminalView.getContainer((JBTerminalWidget)realComponent);
        otherContainer.myPanel = (JPanel)parent;
      }
      realComponent.getParent().remove(realComponent);
      parent.remove(splitter);
      parent.add(realComponent, BorderLayout.CENTER);
      parent.revalidate();
      if (hasFocus) {
        requestFocus(realComponent);
      }
      myTerminalView.unregister(this);
    }
    else {
      myTerminalView.closeTab(myContent);
    }
  }

  private static @NotNull Component unwrapComponent(@NotNull JComponent component) {
    Component[] components = component.getComponents();
    if (components.length == 1) {
      Component c = components[0];
      if (c instanceof JBTerminalWidget || c instanceof Splitter) {
        return c;
      }
    }
    LOG.error("Cannot unwrap " + component);
    return component;
  }

  private void requestFocus(@NotNull Component component) {
    IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(() -> {
      if (component instanceof JBTerminalWidget) {
        ((JBTerminalWidget)component).getTerminalPanel().requestFocusInWindow();
      }
      else {
        component.requestFocusInWindow();
      }
    });
  }
}
